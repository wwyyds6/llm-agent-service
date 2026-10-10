package com.wangqihui.llmagent;

import com.wangqihui.llmagent.entity.Message;
import com.wangqihui.llmagent.mapper.MessageMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

@RestController
public class StreamController {

    /** 系统提示词：由服务端统一控制，不依赖前端传 */

    private static final String SYSTEM_PROMPT =
            "你是一个乐于助人的中文AI助手。请用纯文本自然段落回答，"
                    + "不要使用任何 Markdown 语法（如 ** 加粗、# 标题、- 列表符号），回答简洁准确。"
                    + "重要规则：涉及任何数字计算时必须调用 calculate 工具，不要自己心算；"
                    + "涉及当前日期或时间时必须调用 getCurrentTime 工具。"
                    + "注意：历史对话中出现过的时间信息都是过去的、已经失效的，"
                    + "回答任何与当前时间相关的问题时，必须重新调用工具获取，不得直接复用历史中的时间。";

    /** 最多带入多少条历史消息，防止上下文过长导致 token 爆炸 */
    private static final int MAX_HISTORY = 20;

    private final AgentService agentService;
    // 构造器里加参数并赋值 this.agentService = agentService;
    private final LlmService llmService;
    private final MessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    public StreamController(AgentService agentService, LlmService llmService,
                            MessageMapper messageMapper,
                            ObjectMapper objectMapper) {
        this.agentService = agentService;
        this.llmService = llmService;
        this.messageMapper = messageMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 流式对话
     * @param conversationId 会话ID（前端先调 POST /conversation 创建）
     * @param userMessage    本轮用户输入（纯文本）
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestParam("conversationId") Long conversationId,
                                 @RequestBody String userMessage) {

        // 1. 先把用户消息落库
        Message userMsg = new Message();
        userMsg.setConversationId(conversationId);
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        messageMapper.insert(userMsg);

        // 2. 从数据库读出历史，组装成模型需要的 messages 数组
        String messagesJson = buildMessagesJson(conversationId);

        SseEmitter emitter = new SseEmitter(0L);

        new Thread(() -> {
            StringBuilder fullAnswer = new StringBuilder();
            try {
                llmService.chatStreamWithHistory(messagesJson, delta -> {
                    fullAnswer.append(delta);
                    try {
                        emitter.send(delta);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

                // 3. 流结束后把 AI 回复落库
                Message aiMsg = new Message();
                aiMsg.setConversationId(conversationId);
                aiMsg.setRole("assistant");
                aiMsg.setContent(fullAnswer.toString());
                messageMapper.insert(aiMsg);

                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    /** 系统提示词 + 最近 N 条历史 → OpenAI 格式的 JSON 数组 */
    private String buildMessagesJson(Long conversationId) {
        return buildMessagesArray(conversationId).toString();
    }

    private ArrayNode buildMessagesArray(Long conversationId) {
        List<Message> all = messageMapper.findByConversationId(conversationId);

        List<Message> recent = all.size() > MAX_HISTORY
                ? all.subList(all.size() - MAX_HISTORY, all.size())
                : all;

        ArrayNode arr = objectMapper.createArrayNode();

        ObjectNode sys = objectMapper.createObjectNode();
        sys.put("role", "system");
        sys.put("content", SYSTEM_PROMPT);
        arr.add(sys);

        for (Message m : recent) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("role", m.getRole());
            node.put("content", m.getContent());
            arr.add(node);
        }
        return arr;
    }
    /**
     * Agent 流式对话：正文用 delta 事件推，工具调用用 tool 事件推
     */
    @PostMapping(value = "/agent/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter agentStream(@RequestParam("conversationId") Long conversationId,
                                  @RequestBody String userMessage) {

        // 1. 用户消息落库
        Message userMsg = new Message();
        userMsg.setConversationId(conversationId);
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        messageMapper.insert(userMsg);

        // 2. 组装历史（含系统提示词 + 本轮用户消息）
        ArrayNode messages = buildMessagesArray(conversationId);

        SseEmitter emitter = new SseEmitter(0L);

        new Thread(() -> {
            StringBuilder fullAnswer = new StringBuilder();
            try {
                agentService.chatStreamWithTools(messages,
                        // 正文分片 → delta 事件
                        delta -> {
                            fullAnswer.append(delta);
                            try {
                                ObjectNode payload = objectMapper.createObjectNode();
                                payload.put("text", delta);
                                emitter.send(SseEmitter.event()
                                        .name("delta")
                                        .data(payload.toString()));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        },
                        // 工具调用 → tool 事件
                        toolEvent -> {
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("tool")
                                        .data(toolEvent.toString()));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });

                // 3. AI 回复落库
                if (fullAnswer.length() > 0) {
                    Message aiMsg = new Message();
                    aiMsg.setConversationId(conversationId);
                    aiMsg.setRole("assistant");
                    aiMsg.setContent(fullAnswer.toString());
                    messageMapper.insert(aiMsg);
                }

                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }
}
