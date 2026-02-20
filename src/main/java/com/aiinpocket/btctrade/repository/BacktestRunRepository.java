package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.BacktestRun;
import com.aiinpocket.btctrade.model.enums.BacktestRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

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

    /** 查詢用戶最近 10 筆回測紀錄（JOIN FETCH 避免 LazyInitializationException） */
    @Query("SELECT r FROM BacktestRun r JOIN FETCH r.user JOIN FETCH r.strategyTemplate " +
           "WHERE r.user.id = :userId ORDER BY r.createdAt DESC LIMIT 10")
    List<BacktestRun> findRecentByUserIdWithRelations(Long userId);

    /** 查詢單筆回測紀錄（JOIN FETCH 避免 LazyInitializationException） */
    @Query("SELECT r FROM BacktestRun r JOIN FETCH r.user JOIN FETCH r.strategyTemplate WHERE r.id = :id")
    Optional<BacktestRun> findByIdWithRelations(Long id);

    /** 查詢用戶最近 N 筆回測紀錄 */
    List<BacktestRun> findTop10ByUserIdOrderByCreatedAtDesc(Long userId);

    /** 檢查用戶是否有正在執行的回測（限制每位用戶同時只能有 1 個 RUNNING 回測） */
    boolean existsByUserIdAndStatus(Long userId, BacktestRunStatus status);

    /** 檢查用戶是否有任何指定狀態的回測（PENDING + RUNNING 雙重檢查，防止 race condition） */
    boolean existsByUserIdAndStatusIn(Long userId, List<BacktestRunStatus> statuses);

    /** 查詢所有指定狀態的回測（用於系統監控或清理逾時任務） */
    List<BacktestRun> findByStatus(BacktestRunStatus status);

    /** 查詢多種狀態的回測（啟動時清理卡住的 RUNNING/PENDING） */
    List<BacktestRun> findByStatusIn(List<BacktestRunStatus> statuses);

    /** 刪除指定策略模板的所有回測紀錄（模板遷移/刪除時用） */
    void deleteByStrategyTemplateId(Long strategyTemplateId);
}
