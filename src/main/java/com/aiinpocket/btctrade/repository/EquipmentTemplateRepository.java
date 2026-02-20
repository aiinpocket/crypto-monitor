package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.EquipmentTemplate;
import com.aiinpocket.btctrade.model.enums.CharacterClass;
import com.aiinpocket.btctrade.model.enums.EquipmentType;
import com.aiinpocket.btctrade.model.enums.Rarity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EquipmentTemplateRepository extends JpaRepository<EquipmentTemplate, Long> {

    List<EquipmentTemplate> findByRarity(Rarity rarity);

    List<EquipmentTemplate> findByEquipmentType(EquipmentType type);

    /** 查找某職業可穿的裝備（通用 + 該職業限定） */
    @Query("SELECT e FROM EquipmentTemplate e WHERE e.classRestriction IS NULL OR e.classRestriction = :charClass")
    List<EquipmentTemplate> findEquipableByClass(CharacterClass charClass);
}
