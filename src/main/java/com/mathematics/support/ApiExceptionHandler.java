package com.mathematics.support;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.mathematics.guard.RateLimitedException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException ex) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(ex.code().status());
        if (ex instanceof RateLimitedException limited) {
            response.header("Retry-After", String.valueOf(limited.retryAfterSeconds()));
        }
        return response.body(body(ex.code(), ex.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleInvalid(MethodArgumentNotValidException ex) {
        Map<String, Object> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status())
                .body(body(ErrorCode.VALIDATION_ERROR, "请求参数不合法", fields));
    }

    /**
     * 请求体不是合法 JSON、缺少必填请求头、路径参数类型不对，都属于调用方问题，别报 500。
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex) {
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status())
                .body(body(ErrorCode.VALIDATION_ERROR, "请求格式不正确", null));
    }

    /**
     * 静态资源找不到（比如 favicon）不应该走到兜底的 500 分支。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleMissingResource(NoResourceFoundException ex) {
        return ResponseEntity.status(ErrorCode.NOT_FOUND.status())
                .body(body(ErrorCode.NOT_FOUND, "资源不存在", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception ex) {
        log.error("unhandled error", ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(body(ErrorCode.INTERNAL_ERROR, "服务异常，请稍后再试", null));
    }

    private Map<String, Object> body(ErrorCode code, String message, Map<String, Object> details) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code.name());
        body.put("message", message);
        if (details != null && !details.isEmpty()) {
            body.put("details", details);
        }
        return body;
    }
}
