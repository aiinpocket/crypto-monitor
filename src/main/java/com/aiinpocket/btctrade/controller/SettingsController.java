package com.aiinpocket.btctrade.controller;

import com.aiinpocket.btctrade.model.entity.AppUser;
import com.aiinpocket.btctrade.model.entity.NotificationChannel;
import com.aiinpocket.btctrade.security.AppUserPrincipal;
import com.aiinpocket.btctrade.service.NotificationChannelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * 使用者設定頁面 Controller。
 * 負責渲染通知管道設定頁面（settings.html），
 * 讓使用者可以管理 Discord / Gmail / Telegram 三種通知管道。
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class SettingsController {

    private final NotificationChannelService channelService;

    /**
     * 渲染設定頁面。
     * 從資料庫載入使用者的所有通知管道設定，傳遞到前端 Thymeleaf 模板。
     */
    @GetMapping("/settings")
    public String settings(
            @AuthenticationPrincipal AppUserPrincipal principal,
            Model model) {

        AppUser user = principal.getAppUser();
        log.debug("[設定頁面] 使用者 {} 載入設定頁面", user.getEmail());

        // 取得使用者已設定的通知管道
        List<NotificationChannel> channels = channelService.getChannels(user.getId());

        model.addAttribute("user", user);
        model.addAttribute("channels", channels);
        return "settings";
    }
}
