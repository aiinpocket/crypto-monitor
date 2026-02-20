package com.aiinpocket.btctrade.model.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 怪物掉落表（哪隻怪物可以掉落哪些裝備）。
 * 每個怪物有自己的掉落池，實際掉率 = 裝備基礎掉率 × 報酬率修正。
 */
@Entity
@Table(name = "monster_drop", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"monster_id", "equipment_template_id"})
}, indexes = {
        @Index(name = "idx_drop_monster", columnList = "monster_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonsterDrop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 怪物 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monster_id", nullable = false)
    private Monster monster;

    /** 可掉落的裝備模板 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_template_id", nullable = false)
    private EquipmentTemplate equipmentTemplate;
}
