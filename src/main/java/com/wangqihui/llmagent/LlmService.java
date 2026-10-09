package com.wangqihui.llmagent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

@Service
public class LlmService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;          // ← 新增：字段

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.base-url}")
    private String baseUrl;

    @Value("${deepseek.model}")
    private String model;

    // 构造器注入：两个依赖，Spring 会自动装配
    public LlmService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 非流式调用：等模型全部生成完，一次性返回纯文本
     */
    public String chat(String userMessage) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            String body = """
                    {
                      "model": "%s",
                      "messages": [
                        {"role": "user", "content": "%s"}
                      ]
                    }
                    """.formatted(model, userMessage.replace("\"", "\\\""));

            HttpEntity<String> request = new HttpEntity<>(body, headers);
            String rawJson = restTemplate.postForObject(
                    baseUrl + "/chat/completions", request, String.class);

            // 解析 choices[0].message.content
            JsonNode root = objectMapper.readTree(rawJson);
            String content = root.path("choices").path(0)
                    .path("message").path("content").asText();

            // 打印 token 消耗（后面算成本要用）
            JsonNode usage = root.path("usage");
            System.out.printf("[token] prompt=%d, completion=%d, total=%d%n",
                    usage.path("prompt_tokens").asInt(),
                    usage.path("completion_tokens").asInt(),
                    usage.path("total_tokens").asInt());

            return content;

        } catch (Exception e) {
            throw new RuntimeException("调用大模型失败: " + e.getMessage(), e);
        }
    }

    /**
     * 流式调用：每收到一块内容就通过回调交给调用方
     */
    public void chatStream(String userMessage, Consumer<String> onDelta) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            // 注意这里多了 "stream": true
            String body = """
                    {
                      "model": "%s",
                      "stream": true,
                      "messages": [
                        {"role": "user", "content": "%s"}
                      ]
                    }
                    """.formatted(model, userMessage.replace("\"", "\\\""));

            restTemplate.execute(
                    baseUrl + "/chat/completions",
                    HttpMethod.POST,
                    req -> {
                        req.getHeaders().putAll(headers);
                        req.getBody().write(body.getBytes(StandardCharsets.UTF_8));
                    },
                    resp -> {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(resp.getBody(), StandardCharsets.UTF_8))) {

                            String line;
                            while ((line = reader.readLine()) != null) {
                                // DeepSeek 返回的每行格式：data: {...}
                                if (!line.startsWith("data:")) {
                                    continue;
                                }
                                String json = line.substring(5).trim();

                                // 结束标记
                                if ("[DONE]".equals(json)) {
                                    break;
                                }

                                try {
                                    JsonNode node = objectMapper.readTree(json);
                                    String delta = node.path("choices").path(0)
                                            .path("delta").path("content").asText();
                                    if (!delta.isEmpty()) {
                                        onDelta.accept(delta);
                                    }
                                } catch (Exception ignored) {
                                    // 个别分块解析失败就跳过，不影响整体
                                }
                            }
                        }
                        return null;
                    });

        } catch (Exception e) {
            throw new RuntimeException("流式调用大模型失败: " + e.getMessage(), e);
        }
    }
    /**
     * 带历史记录的流式调用
     * @param historyJson 形如 [{"role":"user","content":"你好"},{"role":"assistant","content":"你好！"}]
     */
    public void chatStreamWithHistory(String historyJson, Consumer<String> onDelta) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            // 直接把前端传来的历史数组作为 messages
            String body = """
                    {
                      "model": "%s",
                      "stream": true,
                      "messages": %s
                    }
                    """.formatted(model, historyJson);

            restTemplate.execute(
                    baseUrl + "/chat/completions",
                    HttpMethod.POST,
                    req -> {
                        req.getHeaders().putAll(headers);
                        req.getBody().write(body.getBytes(StandardCharsets.UTF_8));
                    },
                    resp -> {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(resp.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (!line.startsWith("data:")) continue;
                                String json = line.substring(5).trim();
                                if ("[DONE]".equals(json)) break;
                                try {
                                    JsonNode node = objectMapper.readTree(json);
                                    String delta = node.path("choices").path(0)
                                            .path("delta").path("content").asText();
                                    if (!delta.isEmpty()) onDelta.accept(delta);
                                } catch (Exception ignored) {
                                }
                            }
                        }
                        return null;
                    });

        } catch (Exception e) {
            throw new RuntimeException("流式调用失败: " + e.getMessage(), e);
        }
    }
}