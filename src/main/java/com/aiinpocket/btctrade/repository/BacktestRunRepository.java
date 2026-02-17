package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.BacktestRun;
import com.aiinpocket.btctrade.model.enums.BacktestRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 回測執行紀錄 Repository。
 * 提供回測紀錄的查詢方法，支援：
 * - 查詢用戶的回測歷史（按建立時間倒序）
 * - 檢查用戶是否有正在執行的回測（用於並行限制）
 * - 按狀態篩選回測紀錄
 */
public interface BacktestRunRepository extends JpaRepository<BacktestRun, Long> {

    /** 查詢用戶的所有回測紀錄，按建立時間倒序（最新的在前） */
    List<BacktestRun> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 查詢用戶最近 N 筆回測紀錄 */
    List<BacktestRun> findTop10ByUserIdOrderByCreatedAtDesc(Long userId);

    /** 檢查用戶是否有正在執行的回測（限制每位用戶同時只能有 1 個 RUNNING 回測） */
    boolean existsByUserIdAndStatus(Long userId, BacktestRunStatus status);

    /** 查詢所有指定狀態的回測（用於系統監控或清理逾時任務） */
    List<BacktestRun> findByStatus(BacktestRunStatus status);
}
