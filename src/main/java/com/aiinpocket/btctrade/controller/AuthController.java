package com.aiinpocket.btctrade.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthController {

    @Value("${app.version}")
    private String appVersion;

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("appVersion", appVersion);
        return "login";
    }
}
