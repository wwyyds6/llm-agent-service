package com.wangqihui.llmagent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class LlmService {

    private final RestTemplate restTemplate;

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.base-url}")
    private String baseUrl;

    @Value("${deepseek.model}")
    private String model;

    // 构造器注入（推荐写法，面试也会问为什么不用 @Autowired 字段注入）
    public LlmService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 最简单的调用：发一句话，拿回模型回复
     */
    public String chat(String userMessage) {
        // 1. 请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        // 2. 请求体（注意 content 要转义引号）
        String body = """
                {
                  "model": "%s",
                  "messages": [
                    {"role": "user", "content": "%s"}
                  ]
                }
                """.formatted(model, userMessage.replace("\"", "\\\""));

        // 3. 发请求
        HttpEntity<String> request = new HttpEntity<>(body, headers);
        String response = restTemplate.postForObject(
                baseUrl + "/chat/completions", request, String.class);

        System.out.println("=== DeepSeek 原始返回 ===");
        System.out.println(response);
        System.out.println("=========================");

        return response;
    }
}