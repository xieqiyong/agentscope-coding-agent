package com.agentplatform.web.config;

import com.agentplatform.common.dto.ApiResponse;
import com.agentplatform.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("未处理异常: uri={}, method={}", request.getRequestURI(), request.getMethod(), e);

        // 如果是 SSE 请求，不能返回 JSON ApiResponse（Content-Type 冲突）
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("text/event-stream")) {
            // SSE 请求的异常由 AgentRuntimeController 自己处理，这里不再重复处理
            // 如果走到这里说明是意料之外的异常，直接返回 null 让 Spring 处理
            return null;
        }

        return ApiResponse.error(500, "Internal server error");
    }
}
