package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.CommunityTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * 社群模板 Repository。
 */
public interface CommunityTemplateRepository extends JpaRepository<CommunityTemplate, Long> {

    /** 查詢所有公開的社群模板（按讚數倒序、建立時間倒序） */
    @Query("SELECT ct FROM CommunityTemplate ct " +
           "JOIN FETCH ct.submitter JOIN FETCH ct.strategyTemplate " +
           "WHERE ct.status = 'ACTIVE' ORDER BY ct.upvoteCount DESC, ct.createdAt DESC")
    List<CommunityTemplate> findAllActive();

    /** 查詢提交者的所有社群模板（含 HIDDEN） */
    @Query("SELECT ct FROM CommunityTemplate ct " +
           "JOIN FETCH ct.submitter JOIN FETCH ct.strategyTemplate " +
           "WHERE ct.submitter.id = :submitterId ORDER BY ct.createdAt DESC")
    List<CommunityTemplate> findBySubmitterIdOrderByCreatedAtDesc(Long submitterId);

    /** 計算提交者的公開社群模板數量（ACTIVE） */
    int countBySubmitterIdAndStatus(Long submitterId, String status);

    /** 計算提交者的所有社群模板數量 */
    int countBySubmitterId(Long submitterId);

    /** 檢查某策略模板是否已被分享到社群 */
    boolean existsByStrategyTemplateId(Long strategyTemplateId);

    /** 根據 ID 查詢並 JOIN FETCH 提交者和策略模板 */
    @Query("SELECT ct FROM CommunityTemplate ct " +
           "JOIN FETCH ct.submitter JOIN FETCH ct.strategyTemplate " +
           "WHERE ct.id = :id")
    Optional<CommunityTemplate> findByIdWithRelations(Long id);

    /** 查詢所有已授予額度獎勵的社群模板數量（某提交者） */
    int countBySubmitterIdAndBonusQuotaGrantedTrue(Long submitterId);
}
