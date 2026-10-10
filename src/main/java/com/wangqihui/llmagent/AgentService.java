package com.wangqihui.llmagent;

import com.wangqihui.llmagent.entity.ToolCallLog;
import com.wangqihui.llmagent.mapper.ToolCallLogMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class AgentService {

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;
    private final ToolService toolService;
    private final ToolCallLogMapper toolCallLogMapper;

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

    public AgentService(RestTemplate restTemplate,
                        ObjectMapper mapper,
                        ToolService toolService,
                        ToolCallLogMapper toolCallLogMapper) {
        this.restTemplate = restTemplate;
        this.mapper = mapper;
        this.toolService = toolService;
        this.toolCallLogMapper = toolCallLogMapper;
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

                boolean success = result != null && !result.startsWith("工具执行失败");
                System.out.println("[Agent] 工具返回: " + result + " 耗时: " + cost + "ms");

                // 把这次工具调用记录进数据库（可观测性 + 成本分析）
                ToolCallLog log = new ToolCallLog();
                log.setToolName(toolName);
                log.setArguments(argsJson);
                log.setResult(result);
                log.setCostMs((int) cost);
                log.setSuccess(success);
                toolCallLogMapper.insert(log);

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
    // ==================== 流式 + 工具调用融合 ====================

    /** 工具调用分片的回调：index 用于把分片拼回同一个调用 */
    @FunctionalInterface
    public interface ToolCallDeltaConsumer {
        void accept(int index, String id, String name, String argumentsDelta);
    }

    private static final int MAX_ROUNDS = 5;

    /**
     * 流式 Agent 循环
     * @param messages    初始消息（含系统提示词 + 历史 + 本轮用户消息），会在循环中被追加
     * @param onDelta     正文分片回调（直接推给前端）
     * @param onToolEvent 工具调用通知回调（前端展示"调用了什么工具"）
     */
    public void chatStreamWithTools(ArrayNode messages,
                                    Consumer<String> onDelta,
                                    Consumer<ObjectNode> onToolEvent) {

        for (int round = 1; round <= MAX_ROUNDS; round++) {
            System.out.println("[Agent] 第 " + round + " 轮流式请求模型");

            StringBuilder content = new StringBuilder();

            // 按 index 累积工具调用分片
            Map<Integer, String> ids = new LinkedHashMap<>();
            Map<Integer, String> names = new LinkedHashMap<>();
            Map<Integer, StringBuilder> args = new LinkedHashMap<>();

            streamOnce(messages,
                    delta -> {
                        content.append(delta);
                        onDelta.accept(delta);
                    },
                    (index, id, name, argDelta) -> {
                        if (id != null && !id.isEmpty()) {
                            ids.putIfAbsent(index, id);
                        }
                        if (name != null && !name.isEmpty()) {
                            names.putIfAbsent(index, name);
                        }
                        args.computeIfAbsent(index, k -> new StringBuilder())
                                .append(argDelta == null ? "" : argDelta);
                    });

            // 没有工具调用 → 刚才流式输出的就是最终答案
            if (ids.isEmpty()) {
                System.out.println("[Agent] 得到最终回答");
                return;
            }

            // 有工具调用：先把 assistant 的工具请求消息加进历史
            ObjectNode assistantMsg = mapper.createObjectNode();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", content.toString());

            ArrayNode toolCallsArr = mapper.createArrayNode();
            for (Integer index : ids.keySet()) {
                ObjectNode tc = mapper.createObjectNode();
                tc.put("id", ids.get(index));
                tc.put("type", "function");

                ObjectNode fn = mapper.createObjectNode();
                fn.put("name", names.getOrDefault(index, ""));
                fn.put("arguments", args.getOrDefault(index, new StringBuilder()).toString());
                tc.set("function", fn);

                toolCallsArr.add(tc);
            }
            assistantMsg.set("tool_calls", toolCallsArr);
            messages.add(assistantMsg);

            // 执行每个工具
            for (Integer index : ids.keySet()) {
                String callId = ids.get(index);
                String toolName = names.getOrDefault(index, "");
                String argsJson = args.getOrDefault(index, new StringBuilder()).toString();
                if (argsJson.isBlank()) {
                    argsJson = "{}";
                }

                long start = System.currentTimeMillis();
                String result = toolService.execute(toolName, argsJson);
                long cost = System.currentTimeMillis() - start;
                boolean success = result != null && !result.startsWith("工具执行失败");

                System.out.println("[Agent] 调用工具: " + toolName
                        + " 参数: " + argsJson + " → " + result + " (" + cost + "ms)");

                // 落库
                ToolCallLog log = new ToolCallLog();
                log.setToolName(toolName);
                log.setArguments(argsJson);
                log.setResult(result);
                log.setCostMs((int) cost);
                log.setSuccess(success);
                toolCallLogMapper.insert(log);

                // 通知前端
                ObjectNode ev = mapper.createObjectNode();
                ev.put("name", toolName);
                ev.put("arguments", argsJson);
                ev.put("result", result);
                ev.put("costMs", (int) cost);
                ev.put("success", success);
                onToolEvent.accept(ev);

                // 工具结果回灌模型
                ObjectNode toolMsg = mapper.createObjectNode();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", callId);
                toolMsg.put("content", result);
                messages.add(toolMsg);
            }
        }

        System.out.println("[Agent] 达到最大轮次，停止");
    }

    /** 单轮流式请求：正文分片走 onDelta，工具调用分片走 onToolCallDelta */
    private void streamOnce(ArrayNode messages,
                            Consumer<String> onDelta,
                            ToolCallDeltaConsumer onToolCallDelta) {

        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("stream", true);
        body.set("messages", messages);
        body.set("tools", mapper.readTree(TOOLS_JSON));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        final String bodyStr = body.toString();

        restTemplate.execute(
                baseUrl + "/chat/completions",
                HttpMethod.POST,
                req -> {
                    req.getHeaders().putAll(headers);
                    req.getBody().write(bodyStr.getBytes(StandardCharsets.UTF_8));
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
                                JsonNode delta = mapper.readTree(json)
                                        .path("choices").path(0).path("delta");

                                String text = delta.path("content").asText();
                                if (!text.isEmpty()) {
                                    onDelta.accept(text);
                                }

                                for (JsonNode tc : delta.path("tool_calls")) {
                                    onToolCallDelta.accept(
                                            tc.path("index").asInt(),
                                            tc.path("id").asText(),
                                            tc.path("function").path("name").asText(),
                                            tc.path("function").path("arguments").asText());
                                }
                            } catch (Exception ignored) {
                                // 个别分片解析失败就跳过
                            }
                        }
                    }
                    return null;
                });
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