package com.aiinpocket.btctrade.service;

import com.aiinpocket.btctrade.config.TradingStrategyProperties;
import com.aiinpocket.btctrade.model.entity.AppUser;
import com.aiinpocket.btctrade.model.entity.StrategyTemplate;
import com.aiinpocket.btctrade.repository.StrategyPerformanceRepository;
import com.aiinpocket.btctrade.repository.StrategyTemplateRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final TradingStrategyProperties defaultProps;
    private final StrategyPerformanceService performanceService;

    /** 每位用戶最大自訂模板數量 */
    private static final int MAX_USER_TEMPLATES = 10;

    /**
     * 應用啟動時確保系統預設模板存在。
     * 從 application.yml 的 trading.* 配置生成，標記為 systemDefault=true。
     * 若已存在則跳過（冪等操作）。
     */
    @PostConstruct
    void ensureDefaultTemplate() {
        if (templateRepo.existsBySystemDefaultTrue()) {
            log.debug("[策略模板] 系統預設模板已存在，跳過初始化");
            return;
        }

        StrategyTemplate defaultTemplate = StrategyTemplate.fromProperties(defaultProps)
                .name("系統預設策略")
                .description("系統內建的趨勢跟隨 + 動量確認策略，使用 EMA/MACD 進場、ADX 過濾、RSI 保護、移動停利鎖定利潤")
                .systemDefault(true)
                .user(null)
                .build();

        templateRepo.save(defaultTemplate);
        log.info("[策略模板] 系統預設模板已建立 (id={})", defaultTemplate.getId());
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

        // 先清理績效資料再刪除模板
        perfRepo.deleteByStrategyTemplateId(templateId);
        templateRepo.delete(template);
        log.info("[策略模板] 用戶 {} 刪除模板 {} (id={})", userId, template.getName(), templateId);
    }
}
