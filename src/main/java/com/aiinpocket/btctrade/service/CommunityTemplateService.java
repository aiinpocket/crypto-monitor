package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.model.entity.*;
import com.aiinpocket.btctrade.repository.AppUserRepository;
import com.aiinpocket.btctrade.repository.CommunityTemplateRepository;
import com.aiinpocket.btctrade.repository.StrategyTemplateRepository;
import com.aiinpocket.btctrade.repository.TemplateVoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 社群模板管理服務。
 * 負責社群模板的分享、投票、使用（克隆）和自動下架機制。
 *
 * <p>規則：
 * <ul>
 *   <li>基本額度：每人 3 個社群模板</li>
 *   <li>額度獎勵：模板 ≥ 5 人使用且 80%+ 好評 → +1 額度（每模板僅一次，上限 20）</li>
 *   <li>自動下架：評價人數 ≥ 5 且噓 > 50% → HIDDEN（不可重新上架）</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityTemplateService {

    private final CommunityTemplateRepository communityRepo;
    private final TemplateVoteRepository voteRepo;
    private final StrategyTemplateRepository templateRepo;
    private final AppUserRepository userRepo;
    private final StrategyTemplateService templateService;
    private final GamificationService gamificationService;

    private static final int BASE_QUOTA = 3;
    private static final int MAX_TOTAL_QUOTA = 20;
    private static final int AUTO_HIDE_MIN_VOTES = 5;
    private static final double AUTO_HIDE_DOWNVOTE_RATIO = 0.5;
    private static final int BONUS_MIN_USAGE = 5;
    private static final double BONUS_MIN_UPVOTE_RATIO = 0.8;

    /**
     * 查詢所有公開的社群模板。
     */
    public List<CommunityTemplate> getActiveTemplates() {
        return communityRepo.findAllActive();
    }

    /**
     * 查詢用戶自己分享的社群模板（含 HIDDEN）。
     */
    public List<CommunityTemplate> getMySubmissions(Long userId) {
        return communityRepo.findBySubmitterIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 分享模板到社群。
     *
     * @param strategyTemplateId 要分享的自訂策略模板 ID
     * @param user               提交者
     * @param customName         自訂策略名稱（會自動加上「暱稱/」前綴）
     * @throws IllegalArgumentException 模板不存在、無權分享、系統預設、已分享、超過額度
     */
    @Transactional
    public CommunityTemplate shareTemplate(Long strategyTemplateId, AppUser user, String customName) {
        // 驗證模板存在且屬於用戶
        StrategyTemplate template = templateRepo.findById(strategyTemplateId)
                .orElseThrow(() -> new IllegalArgumentException("策略模板不存在"));
        if (template.isSystemDefault()) {
            throw new IllegalArgumentException("系統預設模板不可分享到社群");
        }
        if (!template.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("只能分享自己的策略模板");
        }
        if (communityRepo.existsByStrategyTemplateId(strategyTemplateId)) {
            throw new IllegalArgumentException("此模板已經分享到社群");
        }

        // 檢查額度
        int currentCount = communityRepo.countBySubmitterId(user.getId());
        int bonusCount = communityRepo.countBySubmitterIdAndBonusQuotaGrantedTrue(user.getId());
        int totalQuota = Math.min(BASE_QUOTA + bonusCount, MAX_TOTAL_QUOTA);
        if (currentCount >= totalQuota) {
            throw new IllegalArgumentException(
                    String.format("社群模板額度已滿（%d/%d）。讓你的模板獲得好評可以增加額度！", currentCount, totalQuota));
        }

        // 建立社群模板
        String displayName = user.getNicknameWithTag() + "/" + (customName != null ? customName : template.getName());
        CommunityTemplate community = CommunityTemplate.builder()
                .submitter(user)
                .strategyTemplate(template)
                .displayName(displayName)
                .description(template.getDescription())
                .build();

        communityRepo.save(community);
        log.info("[社群模板] 用戶 {} 分享模板 '{}' (communityId={}, templateId={})",
                user.getId(), displayName, community.getId(), strategyTemplateId);

        // 遊戲化獎勵
        try {
            gamificationService.awardExp(user, 20, "COMMUNITY_SHARE");
        } catch (Exception e) {
            log.warn("[遊戲化] 社群分享獎勵失敗: userId={}", user.getId());
        }

        return community;
    }

    /**
     * 投票（讚/噓）。每人每模板只能投一票，可改變方向。
     *
     * @param communityTemplateId 社群模板 ID
     * @param userId              投票者 ID
     * @param thumbsUp            true=讚, false=噓
     * @return 更新後的社群模板
     */
    @Transactional
    public CommunityTemplate vote(Long communityTemplateId, Long userId, boolean thumbsUp) {
        CommunityTemplate community = communityRepo.findById(communityTemplateId)
                .orElseThrow(() -> new IllegalArgumentException("社群模板不存在"));

        if (!"ACTIVE".equals(community.getStatus())) {
            throw new IllegalArgumentException("此模板已下架，無法投票");
        }

        // 不能對自己的模板投票
        if (community.getSubmitter().getId().equals(userId)) {
            throw new IllegalArgumentException("不能對自己的模板投票");
        }

        var existingVote = voteRepo.findByUserIdAndCommunityTemplateId(userId, communityTemplateId);

        if (existingVote.isPresent()) {
            TemplateVote vote = existingVote.get();
            if (vote.isThumbsUp() == thumbsUp) {
                throw new IllegalArgumentException("你已經投過相同的票了");
            }
            // 改變投票方向
            vote.setThumbsUp(thumbsUp);
            voteRepo.save(vote);

            if (thumbsUp) {
                community.setUpvoteCount(community.getUpvoteCount() + 1);
                community.setDownvoteCount(community.getDownvoteCount() - 1);
            } else {
                community.setUpvoteCount(community.getUpvoteCount() - 1);
                community.setDownvoteCount(community.getDownvoteCount() + 1);
            }
        } else {
            // 新投票
            AppUser voter = userRepo.getReferenceById(userId);
            TemplateVote vote = TemplateVote.builder()
                    .user(voter)
                    .communityTemplate(community)
                    .thumbsUp(thumbsUp)
                    .build();
            voteRepo.save(vote);

            if (thumbsUp) {
                community.setUpvoteCount(community.getUpvoteCount() + 1);
            } else {
                community.setDownvoteCount(community.getDownvoteCount() + 1);
            }
        }

        communityRepo.save(community);

        // 檢查是否需要自動下架
        checkAutoHide(community);

        // 檢查是否達成額度獎勵條件
        checkBonusQuota(community);

        return community;
    }

    /**
     * 使用（克隆）社群模板。
     * 將社群模板的策略參數克隆為用戶自己的自訂模板。
     *
     * @param communityTemplateId 社群模板 ID
     * @param user                使用者
     * @return 新建立的自訂策略模板
     */
    @Transactional
    public StrategyTemplate useTemplate(Long communityTemplateId, AppUser user) {
        CommunityTemplate community = communityRepo.findByIdWithRelations(communityTemplateId)
                .orElseThrow(() -> new IllegalArgumentException("社群模板不存在"));

        if (!"ACTIVE".equals(community.getStatus())) {
            throw new IllegalArgumentException("此模板已下架");
        }

        // 克隆策略模板
        StrategyTemplate clone = templateService.cloneTemplate(
                community.getStrategyTemplate().getId(), user,
                community.getDisplayName() + " (社群)");

        // 更新使用人數
        community.setUsageCount(community.getUsageCount() + 1);
        communityRepo.save(community);

        // 檢查是否達成額度獎勵條件
        checkBonusQuota(community);

        log.info("[社群模板] 用戶 {} 使用社群模板 '{}' (communityId={})",
                user.getId(), community.getDisplayName(), communityTemplateId);

        return clone;
    }

    /**
     * 刪除自己分享的社群模板。
     */
    @Transactional
    public void deleteSubmission(Long communityTemplateId, Long userId) {
        CommunityTemplate community = communityRepo.findById(communityTemplateId)
                .orElseThrow(() -> new IllegalArgumentException("社群模板不存在"));

        if (!community.getSubmitter().getId().equals(userId)) {
            throw new IllegalArgumentException("只能刪除自己分享的模板");
        }

        communityRepo.delete(community);
        log.info("[社群模板] 用戶 {} 刪除社群模板 '{}' (communityId={})",
                userId, community.getDisplayName(), communityTemplateId);
    }

    /**
     * 查詢用戶對某社群模板的投票（讓前端顯示當前投票狀態）。
     *
     * @return null 表示未投票，true=讚, false=噓
     */
    public Boolean getUserVote(Long communityTemplateId, Long userId) {
        return voteRepo.findByUserIdAndCommunityTemplateId(userId, communityTemplateId)
                .map(TemplateVote::isThumbsUp)
                .orElse(null);
    }

    /**
     * 取得用戶的社群模板額度資訊。
     */
    public QuotaInfo getQuotaInfo(Long userId) {
        int currentCount = communityRepo.countBySubmitterId(userId);
        int bonusCount = communityRepo.countBySubmitterIdAndBonusQuotaGrantedTrue(userId);
        int totalQuota = Math.min(BASE_QUOTA + bonusCount, MAX_TOTAL_QUOTA);
        return new QuotaInfo(currentCount, totalQuota, BASE_QUOTA, bonusCount);
    }

    public record QuotaInfo(int used, int total, int base, int bonus) {}

    // ── 內部方法 ──

    /**
     * 檢查是否需要自動下架。
     * 條件：評價人數 ≥ 5 且噓 > 50%
     */
    private void checkAutoHide(CommunityTemplate community) {
        int totalVotes = community.getUpvoteCount() + community.getDownvoteCount();
        if (totalVotes >= AUTO_HIDE_MIN_VOTES && !"HIDDEN".equals(community.getStatus())) {
            double downvoteRatio = (double) community.getDownvoteCount() / totalVotes;
            if (downvoteRatio > AUTO_HIDE_DOWNVOTE_RATIO) {
                community.setStatus("HIDDEN");
                communityRepo.save(community);
                log.info("[社群模板] '{}' 因負評過多自動下架（讚:{}, 噓:{}, 負評率:{}）",
                        community.getDisplayName(), community.getUpvoteCount(),
                        community.getDownvoteCount(), downvoteRatio * 100);
            }
        }
    }

    /**
     * 檢查是否達成額度獎勵條件。
     * 條件：使用人數 ≥ 5 且好評率 ≥ 80%（每模板僅一次）
     */
    private void checkBonusQuota(CommunityTemplate community) {
        if (community.isBonusQuotaGranted()) return;
        if (community.getUsageCount() < BONUS_MIN_USAGE) return;

        int totalVotes = community.getUpvoteCount() + community.getDownvoteCount();
        if (totalVotes == 0) return;

        double upvoteRatio = (double) community.getUpvoteCount() / totalVotes;
        if (upvoteRatio >= BONUS_MIN_UPVOTE_RATIO) {
            community.setBonusQuotaGranted(true);
            communityRepo.save(community);
            log.info("[社群模板] '{}' 達成額度獎勵條件（使用:{}, 好評率:{}），提交者獲得 +1 額度",
                    community.getDisplayName(), community.getUsageCount(), upvoteRatio * 100);

            // 遊戲化獎勵
            try {
                gamificationService.awardExp(community.getSubmitter(), 50, "COMMUNITY_POPULAR");
            } catch (Exception e) {
                log.warn("[遊戲化] 社群人氣獎勵失敗");
            }
        }
    }
}
