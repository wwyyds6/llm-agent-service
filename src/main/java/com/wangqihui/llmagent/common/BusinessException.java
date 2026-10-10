package com.wangqihui.llmagent.common;

/**
 * 业务异常：用于主动抛出可预期的错误（如"会话不存在"）
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        this(500, message);
    }

    public int getCode() {
        return code;
    }
}