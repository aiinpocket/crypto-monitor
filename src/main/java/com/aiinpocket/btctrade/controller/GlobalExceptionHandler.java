package com.aiinpocket.btctrade.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
        String msg = sanitizeMessage(e.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException e) {
        String msg = sanitizeMessage(e.getMessage());
        return ResponseEntity.status(429).body(Map.of("error", msg));
    }

    @ExceptionHandler(NumberFormatException.class)
    public ResponseEntity<Map<String, String>> handleNumberFormat(NumberFormatException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "數值格式不正確"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "請求格式不正確，請檢查欄位型別"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleNoResource(NoResourceFoundException e) {
        log.debug("靜態資源不存在: {}", e.getResourcePath());
        return ResponseEntity.status(404).body(Map.of("error", "資源不存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneral(Exception e) {
        log.error("[GlobalExceptionHandler] 未預期的錯誤", e);
        return ResponseEntity.internalServerError()
                .body(Map.of("error", "系統發生錯誤，請稍後重試"));
    }

    /** 過濾可能含有敏感資訊的錯誤訊息 */
    private static String sanitizeMessage(String msg) {
        if (msg == null || msg.length() > 200) return "操作失敗，請稍後重試";
        String lower = msg.toLowerCase();
        if (lower.contains("sql") || lower.contains("exception") || lower.contains("constraint")
                || lower.contains("connection") || lower.contains("timeout")
                || lower.contains("password") || lower.contains("token")) {
            return "操作失敗，請稍後重試";
        }
        return msg;
    }
}
