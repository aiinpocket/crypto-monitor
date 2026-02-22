package com.aiinpocket.btctrade.config;

import com.aiinpocket.btctrade.security.CustomOidcUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 安全配置。
 * 使用 Google OAuth2 做為唯一的認證方式，
 * 所有頁面都需要登入，但 WebSocket、Actuator、靜態資源等除外。
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOidcUserService customOidcUserService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // 登入頁面、錯誤頁、靜態資源不需認證
                        .requestMatchers("/login", "/error", "/favicon.ico").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/icons/**").permitAll()
                        .requestMatchers("/manifest.json", "/sw.js").permitAll()
                        // WebSocket 端點放行（由 UserWebSocketInterceptor 自行處理認證）
                        .requestMatchers("/ws/**").permitAll()
                        // Actuator 只放行健康檢查端點（K8s liveness/readiness probe 用）
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // 其餘所有請求都需要認證
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth -> oauth
                        // 自訂登入頁面路徑
                        .loginPage("/login")
                        // 登入成功後導向首頁
                        .defaultSuccessUrl("/", true)
                        // 使用自訂的 OidcUserService 來建立/更新使用者
                        .userInfoEndpoint(userInfo -> userInfo
                                .oidcUserService(customOidcUserService)
                        )
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/login")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                )
                // REST API 和 WebSocket 不使用 CSRF 保護
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/**", "/ws/**")
                );

        return http.build();
    }
}
