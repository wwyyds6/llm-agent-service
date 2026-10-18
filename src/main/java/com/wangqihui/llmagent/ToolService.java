package com.wangqihui.llmagent;

import com.wangqihui.llmagent.entity.StudentScore;
import com.wangqihui.llmagent.mapper.StudentScoreMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
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
    private final StudentScoreMapper studentScoreMapper;

    // Spring 自带的表达式解析器，用它来算数学表达式（不用引第三方库）
    private final ExpressionParser parser = new SpelExpressionParser();

    public ToolService(ObjectMapper mapper, StudentScoreMapper studentScoreMapper) {
        this.mapper = mapper;
        this.studentScoreMapper = studentScoreMapper;
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
                case "queryStudentScore":
                    return queryStudentScore(args.path("student").asText());
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
    /** 工具 3：查询学生成绩（只读） */
    private String queryStudentScore(String student) {
        if (student == null || student.isBlank()) {
            return "错误：未提供学生姓名或学号";
        }

        List<StudentScore> list = studentScoreMapper.findByAny(student.trim());
        if (list.isEmpty()) {
            return "未找到该学生的成绩记录：" + student;
        }

        // 结果裁剪：避免返回过长文本占用上下文、推高 token 成本
        int limit = Math.min(list.size(), 20);

        StringBuilder sb = new StringBuilder();
        sb.append(list.get(0).getStudentName())
                .append("（").append(list.get(0).getStudentNo()).append("）的成绩：\n");

        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < limit; i++) {
            StudentScore s = list.get(i);
            sb.append("- ").append(s.getCourse()).append(": ").append(s.getScore()).append("\n");
            sum = sum.add(s.getScore());
        }
        if (list.size() > limit) {
            sb.append("（共 ").append(list.size()).append(" 条，仅显示前 ").append(limit).append(" 条）\n");
        }

        // BigDecimal 除法必须指定精度和舍入模式，否则除不尽会抛 ArithmeticException
        BigDecimal avg = sum.divide(BigDecimal.valueOf(limit), 2, RoundingMode.HALF_UP);
        sb.append("平均分: ").append(avg);

        return sb.toString();
    }
}