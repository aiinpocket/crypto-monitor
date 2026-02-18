package com.aiinpocket.btctrade.controller;

import com.aiinpocket.btctrade.model.entity.NotificationChannel;
import com.aiinpocket.btctrade.model.enums.ChannelType;
import com.aiinpocket.btctrade.security.AppUserPrincipal;
import com.aiinpocket.btctrade.service.NotificationChannelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * 通知管道管理 REST API。
 * 提供 CRUD 和連線測試端點，讓使用者設定 Discord、Gmail、Telegram 通知。
 */
@RestController
@RequestMapping("/api/user/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationChannelService channelService;
    private final ObjectMapper objectMapper;

    /** 取得當前使用者的所有通知管道 */
    @GetMapping
    public List<NotificationChannel> getChannels(@AuthenticationPrincipal AppUserPrincipal principal) {
        return channelService.getChannels(principal.getUserId());
    }

    /**
     * 新增或更新通知管道。
     * 請求 body 範例：
     * {
     *   "channelType": "DISCORD",
     *   "configJson": "{\"botToken\":\"xxx\",\"channelId\":\"123\"}",
     *   "enabled": true,
     *   "notifyOnEntry": true,
     *   "notifyOnExit": true
     * }
     */
    @PostMapping
    public ResponseEntity<?> saveChannel(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestBody Map<String, Object> body) {
        try {
            ChannelType type = ChannelType.valueOf((String) body.get("channelType"));
            String configJson = (String) body.get("configJson");
            boolean enabled = (Boolean) body.getOrDefault("enabled", true);
            boolean notifyOnEntry = (Boolean) body.getOrDefault("notifyOnEntry", true);
            boolean notifyOnExit = (Boolean) body.getOrDefault("notifyOnExit", true);

            // 驗證 configJson 格式與必填欄位
            String validationError = validateConfigJson(type, configJson);
            if (validationError != null) {
                return ResponseEntity.badRequest().body(Map.of("error", validationError));
            }

            NotificationChannel channel = channelService.saveChannel(
                    principal.getAppUser(), type, configJson, enabled, notifyOnEntry, notifyOnExit);
            return ResponseEntity.ok(Map.of(
                    "id", channel.getId(),
                    "channelType", channel.getChannelType().name(),
                    "enabled", channel.isEnabled()
            ));
        } catch (IllegalArgumentException e) {
            log.warn("[通知API] 儲存管道失敗: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[通知API] 儲存管道意外錯誤", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "儲存失敗，請稍後重試"));
        }
    }

    /** 刪除通知管道 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChannel(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id) {
        channelService.deleteChannel(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }

    /** 測試通知管道連線（會實際發送測試訊息） */
    @PostMapping("/{id}/test")
    public ResponseEntity<?> testChannel(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id) {
        boolean success = channelService.testChannel(principal.getUserId(), id);
        return ResponseEntity.ok(Map.of("success", success));
    }

    /**
     * 驗證通知管道的 configJson 格式與必填欄位。
     * @return 錯誤訊息，null 表示驗證通過
     */
    private String validateConfigJson(ChannelType type, String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return "設定內容不能為空";
        }
        if (configJson.length() > 2000) {
            return "設定內容過長";
        }

        JsonNode cfg;
        try {
            cfg = objectMapper.readTree(configJson);
        } catch (Exception e) {
            return "設定內容格式不正確（需為 JSON）";
        }

        return switch (type) {
            case DISCORD -> {
                String botToken = textValue(cfg, "botToken");
                String channelId = textValue(cfg, "channelId");
                if (botToken.isEmpty()) yield "Discord Bot Token 為必填";
                if (channelId.isEmpty()) yield "Discord Channel ID 為必填";
                if (!channelId.matches("\\d{17,20}")) yield "Discord Channel ID 格式不正確（應為數字）";
                yield null;
            }
            case GMAIL -> {
                String email = textValue(cfg, "recipientEmail");
                if (email.isEmpty()) yield "收件人 Email 為必填";
                if (!email.matches("^[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) yield "Email 格式不正確";
                yield null;
            }
            case TELEGRAM -> {
                String botToken = textValue(cfg, "botToken");
                String chatId = textValue(cfg, "chatId");
                if (botToken.isEmpty()) yield "Telegram Bot Token 為必填";
                if (chatId.isEmpty()) yield "Telegram Chat ID 為必填";
                if (!chatId.matches("-?\\d+")) yield "Telegram Chat ID 格式不正確（應為數字）";
                yield null;
            }
        };
    }

    private static String textValue(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return child != null && child.isTextual() ? child.textValue().trim() : "";
    }
}
