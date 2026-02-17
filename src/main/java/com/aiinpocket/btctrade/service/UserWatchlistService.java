package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.model.entity.AppUser;
import com.aiinpocket.btctrade.model.entity.UserWatchlist;
import com.aiinpocket.btctrade.repository.TrackedSymbolRepository;
import com.aiinpocket.btctrade.repository.UserWatchlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 使用者觀察清單服務。
 * 管理每位使用者自選的交易對觀察清單，
 * 使用者只能從全域 TrackedSymbol 池中選擇已追蹤的幣對加入觀察。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserWatchlistService {

    private final UserWatchlistRepository watchlistRepo;
    private final TrackedSymbolRepository trackedSymbolRepo;

    /**
     * 取得使用者的觀察清單（按排序順序）
     */
    public List<UserWatchlist> getWatchlist(Long userId) {
        return watchlistRepo.findByUserIdOrderBySortOrderAsc(userId);
    }

    /**
     * 取得使用者觀察清單中的所有幣對符號
     */
    public List<String> getWatchlistSymbols(Long userId) {
        return watchlistRepo.findByUserIdOrderBySortOrderAsc(userId)
                .stream()
                .map(UserWatchlist::getSymbol)
                .toList();
    }

    /**
     * 新增幣對到使用者的觀察清單。
     * 會檢查：1) 幣對是否已在全域追蹤 2) 是否已在使用者觀察清單中
     */
    @Transactional
    public UserWatchlist addSymbol(AppUser user, String symbol) {
        String upperSymbol = symbol.toUpperCase();

        if (!trackedSymbolRepo.existsBySymbol(upperSymbol)) {
            log.warn("[觀察清單] 使用者 {} 嘗試加入未追蹤的幣對: {}", user.getId(), upperSymbol);
            throw new IllegalArgumentException("幣對 " + upperSymbol + " 尚未在全域追蹤中");
        }

        if (watchlistRepo.existsByUserIdAndSymbol(user.getId(), upperSymbol)) {
            log.warn("[觀察清單] 使用者 {} 嘗試重複加入幣對: {}", user.getId(), upperSymbol);
            throw new IllegalArgumentException("幣對 " + upperSymbol + " 已在觀察清單中");
        }

        long count = watchlistRepo.countByUserId(user.getId());
        UserWatchlist entry = UserWatchlist.builder()
                .user(user)
                .symbol(upperSymbol)
                .sortOrder((int) count)
                .build();

        watchlistRepo.save(entry);
        log.info("[觀察清單] 使用者 {} 加入幣對: {}", user.getId(), upperSymbol);
        return entry;
    }

    /**
     * 從使用者的觀察清單中移除幣對
     */
    @Transactional
    public void removeSymbol(Long userId, String symbol) {
        String upperSymbol = symbol.toUpperCase();
        watchlistRepo.deleteByUserIdAndSymbol(userId, upperSymbol);
        log.info("[觀察清單] 使用者 {} 移除幣對: {}", userId, upperSymbol);
    }

    /**
     * 查詢所有觀察了指定幣對的使用者 ID 清單。
     * 用於通知分發：當某幣對產生交易訊號時，找出所有需要通知的使用者。
     */
    public List<Long> getUserIdsWatching(String symbol) {
        return watchlistRepo.findUserIdsBySymbol(symbol);
    }
}
