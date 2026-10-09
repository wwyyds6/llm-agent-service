package com.wangqihui.llmagent;

import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class ToolService {

    private final ObjectMapper mapper;

    // Spring 自带的表达式解析器，用它来算数学表达式（不用引第三方库）
    private final ExpressionParser parser = new SpelExpressionParser();

    public ToolService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 根据工具名分发执行
     * @param toolName 模型指定的工具名
     * @param argsJson 模型给的参数（JSON 字符串，如 {"expression":"1234*5678"}）
     */
    public String execute(String toolName, String argsJson) {
        try {
            // 有些工具没有参数，模型可能返回空字符串
            JsonNode args = (argsJson == null || argsJson.isBlank())
                    ? mapper.createObjectNode()
                    : mapper.readTree(argsJson);

            switch (toolName) {
                case "getCurrentTime":
                    return getCurrentTime();
                case "calculate":
                    return calculate(args.path("expression").asText());
                default:
                    return "错误：未知的工具 " + toolName;
            }
        } catch (Exception e) {
            // ⭐ 关键设计：工具执行失败不抛异常，而是把错误信息返回给模型
            //    让模型自己决定怎么处理（重试、换个方式问、或告诉用户）
            return "工具执行失败：" + e.getMessage();
        }
    }

    /** 工具 1：获取当前时间 */
    private String getCurrentTime() {
        return LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss"));
    }

    /** 工具 2：计算数学表达式 */
    private String calculate(String expression) {
        if (expression == null || expression.isBlank()) {
            return "错误：表达式为空";
        }
        Object result = parser.parseExpression(expression).getValue();
        return expression + " = " + result;
    }
}