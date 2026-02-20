package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.UserWatchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserWatchlistRepository extends JpaRepository<UserWatchlist, Long> {

    List<UserWatchlist> findByUserIdOrderBySortOrderAsc(Long userId);

    Optional<UserWatchlist> findByUserIdAndSymbol(Long userId, String symbol);

    boolean existsByUserIdAndSymbol(Long userId, String symbol);

    void deleteByUserIdAndSymbol(Long userId, String symbol);

    /** 查詢觀察此幣對的活躍用戶 ID（排除 7 天未登入的消極用戶） */
    @Query("SELECT DISTINCT w.user.id FROM UserWatchlist w " +
            "WHERE w.symbol = :symbol " +
            "AND w.user.lastLoginAt > :cutoff")
    List<Long> findUserIdsBySymbol(@Param("symbol") String symbol, @Param("cutoff") Instant cutoff);

    /** 查詢觀察此幣對且有啟用策略的活躍用戶 ID（排除系統帳號 + 7 天未登入） */
    @Query("SELECT DISTINCT w.user.id FROM UserWatchlist w " +
            "WHERE w.symbol = :symbol " +
            "AND w.user.activeStrategyTemplateId IS NOT NULL " +
            "AND w.user.oauthProvider != 'SYSTEM' " +
            "AND w.user.lastLoginAt > :cutoff")
    List<Long> findActiveStrategyUserIdsBySymbol(@Param("symbol") String symbol, @Param("cutoff") Instant cutoff);

    long countByUserId(Long userId);

    /** 查找觀察某幣對的所有觀察清單（含用戶關聯） */
    List<UserWatchlist> findBySymbol(String symbol);
}
