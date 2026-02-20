package com.aiinpocket.btctrade.config;

import com.aiinpocket.btctrade.security.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 角色創建攔截器。
 * 當已認證用戶尚未完成角色創建（activeStrategyTemplateId == null）時，
 * 強制重導向到角色創建頁面 /character-select。
 */
@Component
@Slf4j
public class CharacterCreationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();

        // 排除不需要攔截的路徑
        if (isExcluded(path)) {
            return true;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal principal) {
            if (principal.getAppUser().getActiveStrategyTemplateId() == null) {
                log.debug("[角色創建] 用戶 {} 尚未完成角色創建，重導向至 /character-select", principal.getUserId());
                response.sendRedirect("/character-select");
                return false;
            }
        }

        return true;
    }

    private boolean isExcluded(String path) {
        return path.equals("/character-select")
                || path.startsWith("/api/user/character-select")
                || path.equals("/login")
                || path.equals("/logout")
                || path.equals("/error")
                || path.startsWith("/actuator")
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/")
                || path.startsWith("/ws/")
                || path.equals("/favicon.ico");
    }
}
