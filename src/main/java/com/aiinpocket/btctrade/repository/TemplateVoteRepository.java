package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.TemplateVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 模板投票 Repository。
 */
public interface TemplateVoteRepository extends JpaRepository<TemplateVote, Long> {

    /** 查詢用戶對某社群模板的投票紀錄 */
    Optional<TemplateVote> findByUserIdAndCommunityTemplateId(Long userId, Long communityTemplateId);

    /** 檢查用戶是否已對某社群模板投票 */
    boolean existsByUserIdAndCommunityTemplateId(Long userId, Long communityTemplateId);
}
