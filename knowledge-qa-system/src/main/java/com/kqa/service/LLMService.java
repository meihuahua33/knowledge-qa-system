package com.kqa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kqa.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/**
 * LLM 对话服务: 调用 DeepSeek API 生成回答
 *
 * 面试点:
 * - System Prompt: 设定 AI 的角色和行为边界
 * - Temperature: 控制随机性, 0=确定性回答, 1=创造性回答
 * - SSE (Server-Sent Events): 服务端推送, 前端实时看到生成过程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMService {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestTemplate restTemplate;

    @PostConstruct
    public void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30_000);
        factory.setReadTimeout(120_000);  // LLM 生成回答可能很久, 设 2 分钟
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 流式对话: 每收到一段文本就回调 consumer
     *
     * @param systemPrompt 系统提示词(设定 AI 角色)
     * @param userMessage  用户问题(含检索到的上下文)
     * @param onChunk      每收到一段文本的回调
     */
    public void chatStream(String systemPrompt, String userMessage, Consumer<String> onChunk) {
        AppProperties.Llm config = appProperties.getLlm();
        String url = config.getBaseUrl() + "/v1/chat/completions";

        // 构建请求体
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModel());
        body.put("stream", true);  // ★ 开启流式, DeepSeek 会逐 token 返回
        body.put("temperature", 0.7);
        body.put("max_tokens", 2048);
        body.put("messages", Arrays.asList(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());

        // 使用 HttpEntity + execute + ResponseExtractor 处理流式响应
        // RestTemplate.exchange() 会等全部返回,不适合流式
        try {
            byte[] requestBody = objectMapper.writeValueAsBytes(body);

            var requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setBufferRequestBody(false);  // 不缓存请求体

            org.springframework.http.client.ClientHttpRequest request =
                    requestFactory.createRequest(
                            java.net.URI.create(url),
                            org.springframework.http.HttpMethod.POST);

            request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            request.getHeaders().setBearerAuth(config.getApiKey());
            request.getHeaders().set("Accept", "text/event-stream");

            // 写入请求体
            request.getBody().write(requestBody);
            request.getBody().flush();

            // 读取 SSE 流
            try (var response = request.execute();
                 var reader = new BufferedReader(
                         new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    // SSE 格式: "data: {...}" 或 "[DONE]"
                    if (line.isEmpty()) continue;
                    if (line.equals("data: [DONE]")) break;
                    if (!line.startsWith("data: ")) continue;

                    String json = line.substring(6);  // 去掉 "data: " 前缀
                    try {
                        // 解析 JSON, 提取 delta.content
                        Map<String, Object> data = objectMapper.readValue(json, Map.class);
                        List<Map<String, Object>> choices = (List<Map<String, Object>>) data.get("choices");
                        if (choices == null || choices.isEmpty()) continue;

                        Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
                        if (delta == null) continue;

                        String content = (String) delta.get("content");
                        if (content != null && !content.isEmpty()) {
                            onChunk.accept(content);  // 回调给上层
                        }
                    } catch (Exception e) {
                        log.debug("解析 SSE 行失败: {}", line, e);
                    }
                }
            }

        } catch (Exception e) {
            log.error("DeepSeek API 调用失败", e);
            throw new RuntimeException("LLM 调用失败", e);
        }
    }
}
