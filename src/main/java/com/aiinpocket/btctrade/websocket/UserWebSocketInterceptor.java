package com.aiinpocket.btctrade.websocket;

import com.aiinpocket.btctrade.security.AppUserPrincipal;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

public class UserWebSocketInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpSession session = servletRequest.getServletRequest().getSession(false);
            if (session != null) {
                Object contextObj = session.getAttribute(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
                if (contextObj instanceof SecurityContext ctx
                        && ctx.getAuthentication() != null
                        && ctx.getAuthentication().getPrincipal() instanceof AppUserPrincipal principal) {
                    attributes.put("userId", principal.getUserId());
                    attributes.put("userEmail", principal.getAppUser().getEmail());
                }
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // no-op
    }
}
