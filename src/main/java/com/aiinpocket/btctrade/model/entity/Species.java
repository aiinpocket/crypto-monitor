package com.aiinpocket.btctrade.model.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 種族定義（16 種，靈感來源 No Game No Life）。
 * 每個種族搭配 2 性別 × 4 職業 = 8 種外觀組合。
 * 全 16 種族共 128 種角色組合。
 */
@Entity
@Table(name = "species")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Species {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 種族名稱（如「人類」「精靈」「矮人」等） */
    @Column(nullable = false, unique = true, length = 30)
    private String name;

    /** 種族描述/背景故事 */
    @Column(length = 300)
    private String description;

    /** CSS 類名前綴（搭配 gender + class 組合出完整類名） */
    @Column(name = "pixel_css_prefix", nullable = false, length = 30)
    private String pixelCssPrefix;

    /** 解鎖所需等級（0 = 初始可用） */
    @Column(name = "unlock_level", nullable = false)
    @Builder.Default
    private Integer unlockLevel = 0;
}
