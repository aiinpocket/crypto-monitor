package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.UserEquipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserEquipmentRepository extends JpaRepository<UserEquipment, Long> {

    List<UserEquipment> findByUserId(Long userId);

    List<UserEquipment> findByEquippedByMemberId(Long memberId);

    long countByUserId(Long userId);

    List<UserEquipment> findByUserIdAndEquippedByMemberIsNull(Long userId);
}
