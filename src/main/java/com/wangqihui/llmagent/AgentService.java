package com.wangqihui.llmagent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class AgentService {

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;
    private final ToolService toolService;

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.base-url}")
    private String baseUrl;

    @Value("${deepseek.model}")
    private String model;

    /** 工具定义：告诉模型"你有哪些工具可以用" */
    private static final String TOOLS_JSON = """
            [
              {
                "type": "function",
                "function": {
                  "name": "getCurrentTime",
                  "description": "获取当前的日期和时间。当用户询问现在几点、今天几号、当前时间时调用。",
                  "parameters": { "type": "object", "properties": {} }
                }
              },
              {
                "type": "function",
                "function": {
                  "name": "calculate",
                  "description": "计算数学表达式。当用户需要做加减乘除等算术运算时调用。",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "expression": {
                        "type": "string",
                        "description": "要计算的数学表达式，例如 1234*5678 或 (3+5)/2"
                      }
                    },
                    "required": ["expression"]
                  }
                }
              }
            ]
            """;

    public AgentService(RestTemplate restTemplate, ObjectMapper mapper, ToolService toolService) {
        this.restTemplate = restTemplate;
        this.mapper = mapper;
        this.toolService = toolService;
    }

    /**
     * 带工具调用的对话（Agent 循环）
     */
    public String chatWithTools(String userMessage) {
        // 1. 构造消息列表
        ArrayNode messages = mapper.createArrayNode();
        messages.add(message("system", "你是一个中文AI助手，可以调用工具获取真实数据。回答简洁准确。"));
        messages.add(message("user", userMessage));

        // 2. Agent 循环：最多 5 轮，防止模型无限调用工具
        for (int round = 1; round <= 5; round++) {
            System.out.println("[Agent] 第 " + round + " 轮请求模型");
            String rawJson = callModel(messages);

            JsonNode root = mapper.readTree(rawJson);
            JsonNode choice = root.path("choices").path(0);
            JsonNode messageNode = choice.path("message");
            String finishReason = choice.path("finish_reason").asText();

            // 3. 模型没要求调用工具 → 说明它给出了最终答案
            if (!"tool_calls".equals(finishReason)) {
                System.out.println("[Agent] 得到最终回答");
                return messageNode.path("content").asText();
            }

            // 4. 模型要求调用工具
            messages.add(messageNode);   // 把模型的工具请求加入消息历史

            for (JsonNode toolCall : messageNode.path("tool_calls")) {
                String callId = toolCall.path("id").asText();
                String toolName = toolCall.path("function").path("name").asText();
                String argsJson = toolCall.path("function").path("arguments").asText();

                System.out.println("[Agent] 调用工具: " + toolName + " 参数: " + argsJson);

                long start = System.currentTimeMillis();
                String result = toolService.execute(toolName, argsJson);
                long cost = System.currentTimeMillis() - start;

                System.out.println("[Agent] 工具返回: " + result + " 耗时: " + cost + "ms");

                // 5. 工具结果作为 role=tool 的消息回灌给模型
                ObjectNode toolMsg = mapper.createObjectNode();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", callId);
                toolMsg.put("content", result);
                messages.add(toolMsg);
            }
            // 循环回到第 2 步，把工具结果发给模型
        }

        return "抱歉，任务步骤过多，已停止。";
    }

    /** 构造一条消息 */
    private ObjectNode message(String role, String content) {
        ObjectNode node = mapper.createObjectNode();
        node.put("role", role);
        node.put("content", content);
        return node;
    }

    /** 调用模型（带上工具定义） */
    private String callModel(ArrayNode messages) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.set("messages", messages);
        body.set("tools", mapper.readTree(TOOLS_JSON));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        HttpEntity<String> request = new HttpEntity<>(body.toString(), headers);
        return restTemplate.postForObject(baseUrl + "/chat/completions", request, String.class);
    }
}