package com.wangqihui.llmagent;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    private final LlmService llmService;

    public TestController(LlmService llmService) {
        this.llmService = llmService;
    }

    @GetMapping("/test-llm")
    public String testLlm(@RequestParam(defaultValue = "你好，请用一句话介绍你自己") String msg) {
        return llmService.chat(msg);
    }
}