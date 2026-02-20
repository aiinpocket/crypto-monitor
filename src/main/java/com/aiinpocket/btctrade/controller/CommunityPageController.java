package com.aiinpocket.btctrade.controller;

import com.aiinpocket.btctrade.model.entity.AppUser;
import com.aiinpocket.btctrade.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 社群模板頁面 Controller。
 * 渲染社群模板瀏覽和管理的 Thymeleaf 模板。
 */
@Controller
@RequiredArgsConstructor
public class CommunityPageController {

    @GetMapping("/community")
    public String community(
            @AuthenticationPrincipal AppUserPrincipal principal,
            Model model) {
        AppUser user = principal.getAppUser();
        model.addAttribute("user", user);
        return "community";
    }
}
