package com.aiinpocket.btctrade.controller;

import com.aiinpocket.btctrade.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 隊伍管理頁面 Controller。
 */
@Controller
@RequiredArgsConstructor
public class PartyPageController {

    @GetMapping("/party")
    public String partyPage(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        model.addAttribute("user", principal.getAppUser());
        return "party";
    }
}
