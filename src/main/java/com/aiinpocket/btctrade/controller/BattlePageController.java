package com.aiinpocket.btctrade.controller;

import com.aiinpocket.btctrade.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 戰鬥與怪物圖鑑頁面 Controller。
 */
@Controller
@RequiredArgsConstructor
public class BattlePageController {

    @GetMapping("/battle")
    public String battlePage(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        model.addAttribute("user", principal.getAppUser());
        return "battle";
    }
}
