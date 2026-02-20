package com.aiinpocket.btctrade.config;

import com.aiinpocket.btctrade.model.entity.AppUser;
import com.aiinpocket.btctrade.repository.AppUserRepository;
import com.aiinpocket.btctrade.service.StrategyTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * GM 帳號初始化器。
 * 應用啟動時建立 4 個 GM 系統帳號（戰士/法師/刺客/射手），
 * 每個 GM 使用該職業的預設策略，在排行榜上作為基準參照。
 *
 * <p>GM 帳號特徵：
 * <ul>
 *   <li>oauthProvider = "SYSTEM"（與 Google OAuth 用戶區隔）</li>
 *   <li>oauthId = "GM-{CLASS}"</li>
 *   <li>等級 10，經驗值 5000</li>
 *   <li>自動綁定對應職業的預設策略</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GmAccountInitializer {

    private final AppUserRepository userRepo;
    private final StrategyTemplateService templateService;

    private static final List<GmSpec> GM_SPECS = List.of(
            new GmSpec("GM-戰士", "WARRIOR", "GM-WARRIOR", "gm-warrior@system.local"),
            new GmSpec("GM-法師", "MAGE", "GM-MAGE", "gm-mage@system.local"),
            new GmSpec("GM-刺客", "ASSASSIN", "GM-ASSASSIN", "gm-assassin@system.local"),
            new GmSpec("GM-射手", "RANGER", "GM-RANGER", "gm-ranger@system.local")
    );

    private record GmSpec(String displayName, String characterClass, String oauthId, String email) {}

    /**
     * 在 StrategyTemplateService 的 ensureDefaultTemplate() 之後執行。
     * 使用 @Order(1) 確保系統預設模板已建立。
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    @Order(1)
    public void ensureGmAccounts() {
        int created = 0;
        for (GmSpec spec : GM_SPECS) {
            if (!userRepo.existsByOauthProviderAndDisplayName("SYSTEM", spec.displayName())) {
                try {
                    var defaultTemplate = templateService.getDefaultTemplateForClass(spec.characterClass());

                    AppUser gm = AppUser.builder()
                            .oauthProvider("SYSTEM")
                            .oauthId(spec.oauthId())
                            .email(spec.email())
                            .displayName(spec.displayName())
                            .role("USER")
                            .characterClass(spec.characterClass())
                            .level(10)
                            .experience(5000L)
                            .activeStrategyTemplateId(defaultTemplate.getId())
                            .build();
                    userRepo.save(gm);
                    log.info("[GM初始化] 建立 GM 帳號: {} (職業={}, 策略='{}')",
                            spec.displayName(), spec.characterClass(), defaultTemplate.getName());
                    created++;
                } catch (Exception e) {
                    log.warn("[GM初始化] 建立 {} 失敗: {}", spec.displayName(), e.getMessage());
                }
            }
        }

        if (created > 0) {
            log.info("[GM初始化] 共建立 {} 個 GM 帳號", created);
        } else {
            log.debug("[GM初始化] 所有 GM 帳號已存在，跳過初始化");
        }
    }
}
