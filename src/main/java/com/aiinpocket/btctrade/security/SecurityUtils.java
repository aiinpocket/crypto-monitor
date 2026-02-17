package com.aiinpocket.btctrade.security;

import com.aiinpocket.btctrade.model.entity.AppUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static AppUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal p) {
            return p.getAppUser();
        }
        throw new IllegalStateException("No authenticated user");
    }

    public static Long currentUserId() {
        return currentUser().getId();
    }
}
