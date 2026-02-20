package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.UserWatchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserWatchlistRepository extends JpaRepository<UserWatchlist, Long> {

    List<UserWatchlist> findByUserIdOrderBySortOrderAsc(Long userId);

    Optional<UserWatchlist> findByUserIdAndSymbol(Long userId, String symbol);

    boolean existsByUserIdAndSymbol(Long userId, String symbol);

    void deleteByUserIdAndSymbol(Long userId, String symbol);

    @Query("SELECT DISTINCT w.user.id FROM UserWatchlist w WHERE w.symbol = :symbol")
    List<Long> findUserIdsBySymbol(String symbol);

    /** 查詢觀察此幣對且有啟用策略的用戶 ID（非系統帳號） */
    @Query("SELECT DISTINCT w.user.id FROM UserWatchlist w " +
            "WHERE w.symbol = :symbol " +
            "AND w.user.activeStrategyTemplateId IS NOT NULL " +
            "AND w.user.oauthProvider != 'SYSTEM'")
    List<Long> findActiveStrategyUserIdsBySymbol(@Param("symbol") String symbol);

    long countByUserId(Long userId);

    /** 查找觀察某幣對的所有觀察清單（含用戶關聯） */
    List<UserWatchlist> findBySymbol(String symbol);
}
