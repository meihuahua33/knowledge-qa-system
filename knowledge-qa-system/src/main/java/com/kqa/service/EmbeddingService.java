package com.kqa.service;

import com.kqa.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * Embedding 服务: 调用 OpenAI / 兼容 API 将文本转为向量
 *
 * 面试点:
 * - text-embedding-3-small 输出 1536 维向量, 输入最长 8191 tokens
 * - 支持批量输入(最多 2048 条/次), 减少 API 调用次数
 * - 向量本质是语义的数学表示: "猫"和"宠物"的向量距离 < "猫"和"汽车"
 * - 为什么不用 DeepSeek? DeepSeek 目前不提供 Embedding 模型, 只能用 OpenAI
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final AppProperties appProperties;
    private RestTemplate restTemplate;

    @PostConstruct
    public void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30_000);  // 30 秒连接超时
        factory.setReadTimeout(60_000);     // 60 秒读取超时
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 单条文本转向量
     */
    public List<Float> embed(String text) {
        return embedBatch(Collections.singletonList(text)).get(0);
    }

    /**
     * 批量文本转向量
     *
     * POST https://api.openai.com/v1/embeddings
     * Body: {"model": "text-embedding-3-small", "input": ["text1", "text2"]}
     */
    @SuppressWarnings("unchecked")
    public List<List<Float>> embedBatch(List<String> texts) {
        AppProperties.Embedding config = appProperties.getEmbedding();

        // 构建请求体
        Map<String, Object> body = Map.of(
                "model", config.getModel(),
                "input", texts
        );

        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        String url = config.getBaseUrl() + "/v1/embeddings";

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, Map.class);

            if (response.getBody() == null) {
                throw new RuntimeException("Embedding API 返回空响应");
            }

            // 解析响应: data[i].embedding → List<Float>
            List<Map<String, Object>> dataList =
                    (List<Map<String, Object>>) response.getBody().get("data");

            List<List<Float>> result = new ArrayList<>();
            for (Map<String, Object> item : dataList) {
                List<Double> raw = (List<Double>) item.get("embedding");
                List<Float> vector = new ArrayList<>(raw.size());
                for (Double d : raw) {
                    vector.add(d.floatValue());
                }
                result.add(vector);
            }

            log.info("Embedding 完成: 输入 {} 条文本, 输出 {} 个向量", texts.size(), result.size());
            return result;

        } catch (Exception e) {
            log.error("Embedding API 调用失败: url={}, texts={}", url, texts.size(), e);
            throw new RuntimeException("Embedding API 调用失败", e);
        }
    }
}
