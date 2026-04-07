package com.teadrink.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBiz(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.fail(e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldError() != null
                ? e.getBindingResult().getFieldError().getDefaultMessage()
                : "参数校验失败";
        return Result.fail(msg);
    }

    /**
     * 浏览器地址栏访问 /api/login 只会发 GET，而登录接口是 POST，此前会落入 handleOther 显示「系统繁忙」。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.debug("HTTP 方法不允许: {}", e.getMessage());
        return Result.fail("请求方法不正确。/api/login 必须使用 POST，Content-Type: application/json，"
                + "Body 示例：{\"phone\":\"13800138000\",\"password\":\"123456\"}。"
                + "也可在浏览器打开 GET /api/login 查看说明。");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleBodyMissing(HttpMessageNotReadableException e) {
        return Result.fail("请求体不是合法 JSON 或未携带 Body。请使用：Content-Type: application/json，"
                + "Body：{\"phone\":\"13800138000\",\"password\":\"123456\"}");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return Result.fail("系统繁忙，请稍后重试");
    }
}
