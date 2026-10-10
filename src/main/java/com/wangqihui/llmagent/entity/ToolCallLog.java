package com.wangqihui.llmagent.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ToolCallLog {
    private Long id;
    private Long messageId;
    private String toolName;
    private String arguments;
    private String result;
    private Integer costMs;
    private Boolean success;
    private LocalDateTime createdAt;
}