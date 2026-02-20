package com.aiinpocket.btctrade.model.entity;

import com.aiinpocket.btctrade.model.enums.CharacterClass;
import com.aiinpocket.btctrade.model.enums.EquipmentType;
import com.aiinpocket.btctrade.model.enums.Rarity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 裝備定義（全域模板資料）。
 * 每件裝備有獨立的點陣圖、稀有度、職業限定。
 * 純收藏性質，不影響交易策略。
 */
@Entity
@Table(name = "equipment_template", indexes = {
        @Index(name = "idx_equip_rarity", columnList = "rarity"),
        @Index(name = "idx_equip_type", columnList = "equipment_type"),
        @Index(name = "idx_equip_class", columnList = "class_restriction")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipmentTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 裝備名稱 */
    @Column(nullable = false, length = 50)
    private String name;

    /** 裝備描述 */
    @Column(length = 200)
    private String description;

    /** 裝備類型：武器或防具 */
    @Enumerated(EnumType.STRING)
    @Column(name = "equipment_type", nullable = false, length = 10)
    private EquipmentType equipmentType;

    /** 稀有度等級 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Rarity rarity;

    /** 職業限定（null = 所有職業可裝備） */
    @Enumerated(EnumType.STRING)
    @Column(name = "class_restriction", length = 10)
    private CharacterClass classRestriction;

    /** 基礎掉落率（0.0 ~ 1.0），實際掉率受報酬率調整 */
    @Column(name = "drop_rate", nullable = false)
    private Double dropRate;

    /** 賣出價格（遊戲幣） */
    @Column(name = "sell_price", nullable = false)
    @Builder.Default
    private Long sellPrice = 10L;

    /** CSS 類名，對應點陣圖樣式（覆蓋到角色上） */
    @Column(name = "pixel_css_class", nullable = false, length = 50)
    private String pixelCssClass;
}
