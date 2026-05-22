package com.kqa.controller;

import com.kqa.model.SearchResult;
import com.kqa.service.QAService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 问答接口: SSE 流式输出
 *
 * 面试点:
 * - SseEmitter: Spring MVC 的 SSE(Server-Sent Events)实现
 * - SSE vs WebSocket: SSE 是单向(服务端→客户端), WebSocket 是双向
 *   问答场景只需服务端推送, SSE 更轻量
 * - CompletableFuture: 异步编排, 检索和 LLM 在独立线程执行
 */
@Slf4j
@RestController
@RequestMapping("/api/qa")
@RequiredArgsConstructor
public class QAController {

    private final QAService qaService;

    /**
     * 流式问答 — POST /api/qa/ask
     *
     * 请求体: {"question": "公司请假流程是什么?"}
     *
     * 响应(SSE 流):
     *   event: chunk       ← 每段生成文本
     *   data: "根据公司制度"
     *
     *   event: chunk
     *   data: "，请假需要"
     *
     *   event: references  ← 最后返回引用来源
     *   data: [{"content":"...","metadata":{...}}]
     *
     *   event: done
     *   data: "完成"
     */
    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@RequestBody QuestionRequest request) {

        String question = request.getQuestion();
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("问题不能为空");
        }

        // 超时时间 2 分钟, LLM 生成可能比较久
        SseEmitter emitter = new SseEmitter(120_000L);

        // 在另一个线程执行(不阻塞 Tomcat 线程池)
        CompletableFuture.runAsync(() -> {
            try {
                List<Map<String, Object>> references = new ArrayList<>();

                qaService.askStreaming(question, chunk -> {
                    // 每收到一个文本块, 通过 SSE 推给前端
                    try {
                        emitter.send(SseEmitter.event()
                                .name("chunk")
                                .data(chunk));
                    } catch (IOException e) {
                        log.error("SSE 发送失败", e);
                    }
                });

                // 发送引用来源
                emitter.send(SseEmitter.event()
                        .name("references")
                        .data(references));

                // 发送完成信号
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data("完成"));

                emitter.complete();

            } catch (Exception e) {
                log.error("问答处理失败", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("处理失败: " + e.getMessage()));
                    emitter.completeWithError(e);
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

        // 注册超时回调
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时: question={}", question);
            emitter.complete();
        });

        return emitter;
    }

    /**
     * 仅检索(不含 LLM) — GET /api/qa/search?q=xxx
     * 用于调试: 查看检索召回了哪些文档片段
     */
    @GetMapping("/search")
    public List<SearchResult> search(@RequestParam("q") String question) {
        return qaService.search(question);
    }

    @Data
    public static class QuestionRequest {
        private String question;
    }
}
