package com.wangqihui.llmagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重命名会话的请求体
 */
@Data
public class RenameRequest {

    @NotBlank(message = "会话标题不能为空")
    @Size(max = 50, message = "会话标题不能超过 50 个字")
    private String title;
}