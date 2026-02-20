package com.aiinpocket.btctrade.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 分散式鎖服務。
 * 使用 PostgreSQL Advisory Lock 實現跨 Pod 的互斥鎖。
 * 當多個 Pod 同時收到相同的 K 線收盤事件或 Quartz 排程觸發時，
 * 確保只有一個 Pod 執行策略評估或排程任務。
 *
 * <p>Advisory lock 是 session-level 的輕量鎖，不影響資料表操作。
 * pg_try_advisory_lock() 是非阻塞的：取得鎖返回 true，否則返回 false。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedLockService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 嘗試取得分散式鎖（非阻塞）。
     * 如果其他 Pod 已持有相同 lockId 的鎖，立即返回 false。
     *
     * @param lockId 鎖的唯一識別碼（建議使用固定的 hash 值）
     * @return true 如果成功取得鎖
     */
    public boolean tryLock(long lockId) {
        Boolean acquired = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_lock(?)", Boolean.class, lockId);
        return Boolean.TRUE.equals(acquired);
    }

    /**
     * 釋放分散式鎖。
     * 必須在取得鎖的同一個資料庫連線（session）中釋放。
     *
     * @param lockId 鎖的唯一識別碼
     */
    public void unlock(long lockId) {
        jdbcTemplate.queryForObject(
                "SELECT pg_advisory_unlock(?)", Boolean.class, lockId);
    }

    /**
     * 在鎖保護下執行任務。
     * 如果無法取得鎖（其他 Pod 正在處理），直接跳過不執行。
     *
     * @param lockId 鎖的唯一識別碼
     * @param taskName 任務名稱（用於日誌）
     * @param task 要執行的任務
     * @return true 如果任務被執行
     */
    public boolean executeWithLock(long lockId, String taskName, Runnable task) {
        if (!tryLock(lockId)) {
            log.debug("[分散式鎖] {} 已被其他 Pod 處理，跳過 (lockId={})", taskName, lockId);
            return false;
        }
        try {
            task.run();
            return true;
        } finally {
            unlock(lockId);
        }
    }
}
