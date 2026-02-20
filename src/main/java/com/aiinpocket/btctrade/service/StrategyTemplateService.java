package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.config.TradingStrategyProperties;
import com.aiinpocket.btctrade.model.entity.AppUser;
import com.aiinpocket.btctrade.model.entity.StrategyTemplate;
import com.aiinpocket.btctrade.repository.BacktestRunRepository;
import com.aiinpocket.btctrade.repository.StrategyPerformanceRepository;
import com.aiinpocket.btctrade.repository.StrategyTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 策略模板管理服務。
 * 負責策略模板的 CRUD 操作，包括：
 * <ul>
 *   <li>應用啟動時自動建立系統預設模板（從 application.yml 的配置轉換）</li>
 *   <li>用戶克隆系統預設或其他模板作為自訂模板</li>
 *   <li>用戶更新自訂模板的參數（系統預設模板不允許修改）</li>
 *   <li>查詢用戶可用的所有模板（系統預設 + 自建）</li>
 * </ul>
 *
 * <p>每位用戶最多可建立 10 個自訂模板，防止濫用。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StrategyTemplateService {

    private final StrategyTemplateRepository templateRepo;
    private final StrategyPerformanceRepository perfRepo;
    private final BacktestRunRepository backtestRunRepo;
    private final TradingStrategyProperties defaultProps;
    private final StrategyPerformanceService performanceService;
    private final GamificationService gamificationService;

    /** 每位用戶最大自訂模板數量 */
    private static final int MAX_USER_TEMPLATES = 10;

    /**
     * 四職業預設模板定義（名稱、描述、參數）。
     * 參數經過 BTCUSDT 2021-2026 五年期回測驗證，核心共通點：
     * 極緊移動停利偏移（0.001=0.1%）是所有策略的關鍵。
     *
     * <p>回測結果摘要（5年期 BTCUSDT）：
     * - 戰士：+31.2% 年化, -38.9% MaxDD, Sharpe 1.79
     * - 法師：+54.4% 年化, -55.6% MaxDD, Sharpe 3.14
     * - 遊俠：+27.8% 年化, -26.0% MaxDD, Sharpe 3.62
     * - 刺客：+44.6% 年化, -54.4% MaxDD, Sharpe 2.11
     */
    private static final List<DefaultTemplateSpec> CLASS_TEMPLATES = List.of(
            new DefaultTemplateSpec(
                    "⚔️ 戰士 — 攻守兼備",
                    "平衡型策略：4% 停損 + 極緊移動停利，80% 倉位控制風險。兼顧勝率（68%）與報酬，適合多數市場環境。",
                    new TradingStrategyProperties(
                            new TradingStrategyProperties.StrategyParams(12, 26, 14, 12, 26, 9, 20, 10),
                            new TradingStrategyProperties.RiskParams(0.04, 5, 10000, 5, 1, 0.02, 0.001, 2, 0, 0.8),
                            new TradingStrategyProperties.RsiParams(30, 65, 35, 70, 75, 25)
                    )),
            new DefaultTemplateSpec(
                    "🔮 法師 — 趨勢跟蹤",
                    "趨勢捕手：5% 寬停損 + 10 天長持倉，全倉追蹤大波段。年化 54%+ 但需承受 55% 回撤，適合高風險偏好者。",
                    new TradingStrategyProperties(
                            new TradingStrategyProperties.StrategyParams(12, 26, 14, 12, 26, 9, 20, 10),
                            new TradingStrategyProperties.RiskParams(0.05, 10, 10000, 3, 1, 0.03, 0.001, 4, 0, 1.0),
                            new TradingStrategyProperties.RsiParams(25, 70, 30, 75, 80, 20)
                    )),
            new DefaultTemplateSpec(
                    "🏹 遊俠 — 穩健防守",
                    "防守大師：40% 倉位 + 寬 RSI 過濾，追求最低回撤（-26%）與最高 Sharpe（3.6）。適合保守型交易者。",
                    new TradingStrategyProperties(
                            new TradingStrategyProperties.StrategyParams(12, 26, 14, 12, 26, 9, 20, 10),
                            new TradingStrategyProperties.RiskParams(0.05, 7, 10000, 5, 1, 0.03, 0.001, 3, 0, 0.4),
                            new TradingStrategyProperties.RsiParams(25, 70, 30, 75, 80, 20)
                    )),
            new DefaultTemplateSpec(
                    "🗡️ 刺客 — 短線爆發",
                    "閃電戰：快速 EMA(8/21) + 3% 緊停損 + 2 天速戰速決。交易頻率最高，年化 44%+，適合活躍市場。",
                    new TradingStrategyProperties(
                            new TradingStrategyProperties.StrategyParams(8, 21, 14, 12, 26, 9, 20, 10),
                            new TradingStrategyProperties.RiskParams(0.03, 2, 10000, 8, 1, 0.015, 0.001, 1, 0, 1.0),
                            new TradingStrategyProperties.RsiParams(30, 65, 35, 70, 75, 25)
                    ))
    );

    private record DefaultTemplateSpec(String name, String description, TradingStrategyProperties props) {}

    /**
     * 應用啟動時確保四個職業預設模板存在。
     * 逐一檢查各職業模板是否已建立，缺少的才建立（冪等操作）。
     * 若偵測到舊版單一預設模板（"系統預設策略"），自動遷移為四職業版本。
     *
     * <p>使用 @EventListener 而非 @PostConstruct，因為 @PostConstruct 在 AOP proxy
     * 建立之前執行，導致 @Transactional 無效。ApplicationReadyEvent 觸發時 proxy
     * 已就緒，事務管理正常運作。
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void ensureDefaultTemplate() {
        // 遷移：如果存在舊版單一預設模板，刪除它（連同績效資料和回測紀錄）
        templateRepo.findAllBySystemDefaultTrue().stream()
                .filter(t -> "系統預設策略".equals(t.getName()))
                .forEach(old -> {
                    log.info("[策略模板] 偵測到舊版預設模板 '{}' (id={})，將遷移為四職業版本",
                            old.getName(), old.getId());
                    backtestRunRepo.deleteByStrategyTemplateId(old.getId());
                    perfRepo.deleteByStrategyTemplateId(old.getId());
                    templateRepo.delete(old);
                });

        // 逐一建立缺少的職業模板
        int created = 0;
        for (DefaultTemplateSpec spec : CLASS_TEMPLATES) {
            if (!templateRepo.existsByNameAndSystemDefaultTrue(spec.name())) {
                StrategyTemplate template = StrategyTemplate.fromProperties(spec.props())
                        .name(spec.name())
                        .description(spec.description())
                        .systemDefault(true)
                        .user(null)
                        .build();
                templateRepo.save(template);
                log.info("[策略模板] 職業預設模板已建立: '{}' (id={})", spec.name(), template.getId());
                created++;
            }
        }

        if (created > 0) {
            log.info("[策略模板] 共建立 {} 個職業預設模板", created);
        } else {
            log.debug("[策略模板] 所有職業預設模板已存在，跳過初始化");
        }
    }

    /**
     * 查詢用戶可用的所有策略模板。
     * 包含全域系統預設模板 + 該用戶自建的模板。
     */
    public List<StrategyTemplate> getTemplatesForUser(Long userId) {
        return templateRepo.findByUserIdOrSystemDefaultTrue(userId);
    }

    /**
     * 根據 ID 查詢策略模板。
     * 驗證用戶有權存取該模板（系統預設模板所有人可用，自建模板僅限本人）。
     *
     * @throws IllegalArgumentException 模板不存在或無權存取
     */
    public StrategyTemplate getTemplate(Long templateId, Long userId) {
        StrategyTemplate template = templateRepo.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("策略模板不存在: id=" + templateId));

        // 系統預設模板所有人可讀，自建模板僅限本人
        if (!template.isSystemDefault() && !template.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("無權存取此策略模板");
        }
        return template;
    }

    /**
     * 克隆策略模板。
     * 將來源模板的所有參數複製到新模板，歸屬於指定用戶。
     * 新模板標記為非系統預設（可修改）。
     *
     * @param sourceId 要克隆的來源模板 ID
     * @param user     新模板的擁有者
     * @param newName  新模板的名稱
     * @return 新建立的模板
     * @throws IllegalArgumentException 來源模板不存在，或超過模板數量上限
     */
    @Transactional
    public StrategyTemplate cloneTemplate(Long sourceId, AppUser user, String newName) {
        // 檢查用戶模板數量上限
        int count = templateRepo.countByUserId(user.getId());
        if (count >= MAX_USER_TEMPLATES) {
            throw new IllegalArgumentException(
                    String.format("每位用戶最多可建立 %d 個自訂模板（目前已有 %d 個）", MAX_USER_TEMPLATES, count));
        }

        StrategyTemplate source = templateRepo.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("來源策略模板不存在: id=" + sourceId));

        // 使用來源模板的參數建立新模板
        StrategyTemplate clone = StrategyTemplate.fromProperties(source.toProperties())
                .name(newName != null ? newName : source.getName() + " (副本)")
                .description(source.getDescription())
                .systemDefault(false)
                .user(user)
                .build();

        templateRepo.save(clone);
        log.info("[策略模板] 用戶 {} 從模板 {} 克隆新模板 {} (id={})",
                user.getId(), sourceId, clone.getName(), clone.getId());

        // 非同步計算新模板的績效
        performanceService.computePerformanceAsync(clone.getId());

        // 遊戲化：克隆策略獎勵
        try {
            gamificationService.awardExp(user, 15, "STRATEGY_CLONE");
            gamificationService.checkAndUnlockAchievements(user, "STRATEGY");
        } catch (Exception e) {
            log.warn("[遊戲化] 策略克隆獎勵失敗: userId={}", user.getId());
        }

        return clone;
    }

    /**
     * 更新用戶自訂模板的參數。
     * 系統預設模板不允許修改。
     *
     * @param templateId 要更新的模板 ID
     * @param userId     操作的用戶 ID（驗證權限）
     * @param updates    包含新參數值的模板物件（只更新非 null 的欄位）
     * @throws IllegalArgumentException 模板不存在、無權修改、或為系統預設模板
     */
    @Transactional
    public StrategyTemplate updateTemplate(Long templateId, Long userId, StrategyTemplate updates) {
        StrategyTemplate template = templateRepo.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("策略模板不存在: id=" + templateId));

        if (template.isSystemDefault()) {
            throw new IllegalArgumentException("系統預設模板不可修改，請先克隆後再修改");
        }
        if (!template.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("無權修改此策略模板");
        }

        // 更新所有參數欄位
        if (updates.getName() != null) template.setName(updates.getName());
        if (updates.getDescription() != null) template.setDescription(updates.getDescription());

        // 技術指標參數
        template.setEmaShort(updates.getEmaShort());
        template.setEmaLong(updates.getEmaLong());
        template.setRsiPeriod(updates.getRsiPeriod());
        template.setMacdShort(updates.getMacdShort());
        template.setMacdLong(updates.getMacdLong());
        template.setMacdSignal(updates.getMacdSignal());
        template.setDonchianEntry(updates.getDonchianEntry());
        template.setDonchianExit(updates.getDonchianExit());

        // 風控參數
        template.setStopLossPct(updates.getStopLossPct());
        template.setMaxHoldingDays(updates.getMaxHoldingDays());
        template.setInitialCapital(updates.getInitialCapital());
        template.setMaxTradesPerDay(updates.getMaxTradesPerDay());
        template.setLeverage(updates.getLeverage());
        template.setTrailingActivatePct(updates.getTrailingActivatePct());
        template.setTrailingOffsetPct(updates.getTrailingOffsetPct());
        template.setTimeStopDays(updates.getTimeStopDays());
        template.setCooldownDays(updates.getCooldownDays());
        template.setPositionSizePct(updates.getPositionSizePct());

        // RSI 參數
        template.setRsiLongEntryMin(updates.getRsiLongEntryMin());
        template.setRsiLongEntryMax(updates.getRsiLongEntryMax());
        template.setRsiShortEntryMin(updates.getRsiShortEntryMin());
        template.setRsiShortEntryMax(updates.getRsiShortEntryMax());
        template.setRsiLongExitExtreme(updates.getRsiLongExitExtreme());
        template.setRsiShortExitExtreme(updates.getRsiShortExitExtreme());

        templateRepo.save(template);
        log.info("[策略模板] 用戶 {} 更新模板 {} (id={})", userId, template.getName(), templateId);

        // 參數變更後非同步重算績效
        performanceService.computePerformanceAsync(templateId);

        return template;
    }

    /**
     * 刪除用戶自訂模板。
     * 系統預設模板不可刪除。
     */
    @Transactional
    public void deleteTemplate(Long templateId, Long userId) {
        StrategyTemplate template = templateRepo.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("策略模板不存在: id=" + templateId));

        if (template.isSystemDefault()) {
            throw new IllegalArgumentException("系統預設模板不可刪除");
        }
        if (!template.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("無權刪除此策略模板");
        }

        // 先清理關聯資料再刪除模板
        backtestRunRepo.deleteByStrategyTemplateId(templateId);
        perfRepo.deleteByStrategyTemplateId(templateId);
        templateRepo.delete(template);
        log.info("[策略模板] 用戶 {} 刪除模板 {} (id={})", userId, template.getName(), templateId);
    }
}
