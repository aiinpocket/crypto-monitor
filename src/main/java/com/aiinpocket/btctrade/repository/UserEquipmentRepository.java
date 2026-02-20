package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.UserEquipment;
import com.aiinpocket.btctrade.model.enums.EquipmentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserEquipmentRepository extends JpaRepository<UserEquipment, Long> {

    List<UserEquipment> findByUserId(Long userId);

    List<UserEquipment> findByEquippedByMemberId(Long memberId);

    long countByUserId(Long userId);

    List<UserEquipment> findByUserIdAndEquippedByMemberIsNull(Long userId);

    /** 查詢用戶角色身上的裝備 */
    List<UserEquipment> findByUserIdAndEquippedByUserTrue(Long userId);

    /** 查詢用戶角色身上特定類型的裝備 */
    @Query("SELECT ue FROM UserEquipment ue JOIN ue.equipmentTemplate et " +
           "WHERE ue.user.id = :userId AND ue.equippedByUser = true AND et.equipmentType = :type")
    Optional<UserEquipment> findEquippedByUserAndType(@Param("userId") Long userId,
                                                       @Param("type") EquipmentType type);

    /** 查詢用戶特定一件裝備 */
    Optional<UserEquipment> findByIdAndUserId(Long id, Long userId);

    /** 按取得時間倒序 */
    List<UserEquipment> findByUserIdOrderByAcquiredAtDesc(Long userId);
}
