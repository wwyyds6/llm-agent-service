package com.wangqihui.llmagent;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class StreamController {

    private final LlmService llmService;

    public StreamController(LlmService llmService) {
        this.llmService = llmService;
    }

    /**
     * 流式对话（带历史记录）
     * 前端 POST 一个 JSON 字符串，内容是历史消息数组
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody String historyJson) {

        SseEmitter emitter = new SseEmitter(0L);

        new Thread(() -> {
            try {
                llmService.chatStreamWithHistory(historyJson, delta -> {
                    try {
                        emitter.send(delta);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }
}