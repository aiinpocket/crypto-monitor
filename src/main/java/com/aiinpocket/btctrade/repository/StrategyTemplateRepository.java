package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.StrategyTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * 策略模板 Repository。
 * 提供策略模板的資料庫查詢方法，支援：
 * - 查詢用戶可用的模板（系統預設 + 用戶自建）
 * - 檢查系統預設模板是否存在
 * - 按用戶篩選自建模板
 */
public interface StrategyTemplateRepository extends JpaRepository<StrategyTemplate, Long> {

    /**
     * 查詢用戶可用的所有策略模板。
     * 包含系統預設模板（systemDefault=true）和該用戶自建的模板。
     * 結果按系統預設優先排序（系統預設在前），再按建立時間排序。
     */
    @Query("SELECT t FROM StrategyTemplate t WHERE t.systemDefault = true OR t.user.id = :userId ORDER BY t.systemDefault DESC, t.createdAt ASC")
    List<StrategyTemplate> findByUserIdOrSystemDefaultTrue(Long userId);

    /** 查詢是否存在系統預設模板（應用啟動時用於初始化檢查） */
    boolean existsBySystemDefaultTrue();

    /** 查詢系統預設模板（可能有多個） */
    List<StrategyTemplate> findAllBySystemDefaultTrue();

    /** 按名稱查詢系統預設模板是否存在 */
    boolean existsByNameAndSystemDefaultTrue(String name);

    /** 查詢用戶自建的所有模板（不含系統預設） */
    List<StrategyTemplate> findByUserIdAndSystemDefaultFalse(Long userId);

    /** 計算用戶自建的模板數量（用於限制每位用戶的模板上限） */
    int countByUserId(Long userId);

    /** 按名稱前綴查詢系統預設模板（用於職業→策略映射） */
    Optional<StrategyTemplate> findFirstByNameStartingWithAndSystemDefaultTrue(String namePrefix);
}
