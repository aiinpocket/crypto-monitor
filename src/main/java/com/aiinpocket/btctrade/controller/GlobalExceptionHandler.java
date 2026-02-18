package com.aiinpocket.btctrade.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 全域 REST API 異常處理器。
 * 攔截未被個別 Controller 處理的異常，回傳統一的錯誤格式。
 * 確保前端永遠收到有效的 JSON 錯誤回應，而非原始堆疊追蹤。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(429).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneral(Exception e) {
        log.error("[GlobalExceptionHandler] 未預期的錯誤", e);
        return ResponseEntity.internalServerError()
                .body(Map.of("error", "系統發生錯誤，請稍後重試"));
    }
}
