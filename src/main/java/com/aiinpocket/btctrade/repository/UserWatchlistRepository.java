package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.UserWatchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserWatchlistRepository extends JpaRepository<UserWatchlist, Long> {

    List<UserWatchlist> findByUserIdOrderBySortOrderAsc(Long userId);

    Optional<UserWatchlist> findByUserIdAndSymbol(Long userId, String symbol);

    boolean existsByUserIdAndSymbol(Long userId, String symbol);

    void deleteByUserIdAndSymbol(Long userId, String symbol);

    @Query("SELECT DISTINCT w.user.id FROM UserWatchlist w WHERE w.symbol = :symbol")
    List<Long> findUserIdsBySymbol(String symbol);

    long countByUserId(Long userId);
}
