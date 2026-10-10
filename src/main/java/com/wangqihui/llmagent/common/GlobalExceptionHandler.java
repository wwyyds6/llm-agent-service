package com.wangqihui.llmagent.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：所有 Controller 抛出的异常都在这里统一转成 Result
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 参数校验失败（配合 @Valid 使用） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = (fieldError == null) ? "参数校验失败" : fieldError.getDefaultMessage();
        log.warn("参数校验失败: {}", msg);
        return Result.fail(400, msg);
    }

    /** 业务异常 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** 兜底：其他所有未捕获异常 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        // 注意：系统异常要打完整堆栈（方便排查），但不要返回给前端（防泄露内部信息）
        log.error("系统异常", e);
        return Result.fail(500, "系统繁忙，请稍后重试");
    }
    /** 静态资源/路径不存在：属于正常 404，不打 ERROR 堆栈 */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNotFound(NoResourceFoundException e) {
        log.warn("资源不存在: {}", e.getResourcePath());
        return Result.fail(404, "资源不存在");
    }
}