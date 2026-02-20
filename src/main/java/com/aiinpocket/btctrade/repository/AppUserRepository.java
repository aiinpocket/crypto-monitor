package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByOauthProviderAndOauthId(String oauthProvider, String oauthId);

    Optional<AppUser> findByEmail(String email);

    boolean existsByOauthProviderAndOauthId(String oauthProvider, String oauthId);

    /** 排行榜 Top N（避免全表掃描 + 應用層排序） */
    @Query("SELECT u FROM AppUser u ORDER BY u.level DESC, u.experience DESC LIMIT 10")
    List<AppUser> findTop10ByOrderByLevelDescExperienceDesc();

    /** 查詢 GM 系統帳號是否存在 */
    boolean existsByOauthProviderAndDisplayName(String oauthProvider, String displayName);

    /** 查詢所有已啟用策略的用戶（用於即時信號評估） */
    @Query("SELECT u FROM AppUser u WHERE u.activeStrategyTemplateId IS NOT NULL AND u.oauthProvider != 'SYSTEM'")
    List<AppUser> findAllWithActiveStrategy();
}
