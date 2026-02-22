package com.aiinpocket.btctrade.security;

import com.aiinpocket.btctrade.model.entity.AppUser;
import com.aiinpocket.btctrade.repository.AppUserRepository;
import com.aiinpocket.btctrade.service.GamificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 自訂 OIDC 使用者服務。
 * 在 Google OAuth2 登入流程中被呼叫，負責：
 * 1. 首次登入 → 自動在資料庫建立 AppUser 記錄
 * 2. 後續登入 → 更新顯示名稱、頭像、最後登入時間
 * 3. 將 OidcUser 包裝為 AppUserPrincipal，供後續 Controller 取用
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOidcUserService extends OidcUserService {

    private final AppUserRepository appUserRepo;
    private final GamificationService gamificationService;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        String oauthId = oidcUser.getSubject();
        String email = oidcUser.getEmail();
        String name = oidcUser.getFullName();
        String avatar = oidcUser.getPicture();

        // 嘗試從資料庫查找已存在的使用者，若不存在則建立新的
        AppUser appUser = appUserRepo.findByOauthProviderAndOauthId("GOOGLE", oauthId)
                .orElseGet(() -> {
                    String nickname = AppUser.generateRandomNickname();
                    log.info("[認證] 首次登入，建立新使用者: email={}, nickname={}", email, nickname);
                    return AppUser.builder()
                            .oauthProvider("GOOGLE")
                            .oauthId(oauthId)
                            .email(email)
                            .displayName(name)
                            .avatarUrl(avatar)
                            .nickname(nickname)
                            .role("USER")
                            .build();
                });

        // 每次登入都更新這些欄位（Google 個人資料可能會變更）
        appUser.setLastLoginAt(Instant.now());
        appUser.setDisplayName(name);
        appUser.setAvatarUrl(avatar);
        appUser.setTotalLogins(appUser.getTotalLogins() + 1);
        appUserRepo.save(appUser);

        // 遊戲化：登入經驗 + 每日獎勵 + 成就檢查
        try {
            gamificationService.awardExp(appUser, 5, "LOGIN");
            gamificationService.claimDailyReward(appUser);
            gamificationService.checkAndUnlockAchievements(appUser, "LOGIN");
        } catch (Exception e) {
            log.warn("[遊戲化] 登入獎勵處理失敗: userId={}, error={}", appUser.getId(), e.getMessage());
        }

        log.info("[認證] 使用者登入成功: id={}, email={}, level={}", appUser.getId(), email, appUser.getLevel());

        return new AppUserPrincipal(oidcUser, appUser);
    }
}
