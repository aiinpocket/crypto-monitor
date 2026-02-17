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

            NotificationChannel channel = channelService.saveChannel(
                    principal.getAppUser(), type, configJson, enabled, notifyOnEntry, notifyOnExit);
            return ResponseEntity.ok(Map.of(
                    "id", channel.getId(),
                    "channelType", channel.getChannelType().name(),
                    "enabled", channel.isEnabled()
            ));
        } catch (Exception e) {
            log.warn("[通知API] 儲存管道失敗: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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
    public ResponseEntity<?> testChannel(@PathVariable Long id) {
        boolean success = channelService.testChannel(id);
        return ResponseEntity.ok(Map.of("success", success));
    }
}
