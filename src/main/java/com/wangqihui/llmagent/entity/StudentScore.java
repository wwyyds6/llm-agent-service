package com.wangqihui.llmagent.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class StudentScore {
    private Long id;
    private String studentNo;
    private String studentName;
    private String course;
    private BigDecimal score;     // ⚠️ 注意：用 BigDecimal 不用 double
    private String semester;
    private LocalDateTime createdAt;
}