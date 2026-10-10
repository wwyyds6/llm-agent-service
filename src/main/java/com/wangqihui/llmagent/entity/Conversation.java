package com.wangqihui.llmagent.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Conversation {
    private Long id;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}