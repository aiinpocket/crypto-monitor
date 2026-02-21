package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.UserEquipment;
import com.aiinpocket.btctrade.model.enums.EquipmentType;
import org.springframework.data.jpa.repository.EntityGraph;
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

    /** 查詢用戶角色身上的裝備（eager fetch template 避免 open-in-view=false 時 LazyInit） */
    @EntityGraph(attributePaths = {"equipmentTemplate"})
    List<UserEquipment> findByUserIdAndEquippedByUserTrue(Long userId);

    /** 查詢用戶角色身上特定類型的裝備 */
    @Query("SELECT ue FROM UserEquipment ue JOIN FETCH ue.equipmentTemplate et " +
           "WHERE ue.user.id = :userId AND ue.equippedByUser = true AND et.equipmentType = :type")
    Optional<UserEquipment> findEquippedByUserAndType(@Param("userId") Long userId,
                                                       @Param("type") EquipmentType type);

    /** 查詢用戶特定一件裝備（eager fetch template + user） */
    @EntityGraph(attributePaths = {"equipmentTemplate", "user"})
    Optional<UserEquipment> findByIdAndUserId(Long id, Long userId);

    /** 按取得時間倒序（eager fetch template 避免 N+1 和 LazyInit） */
    @EntityGraph(attributePaths = {"equipmentTemplate"})
    List<UserEquipment> findByUserIdOrderByAcquiredAtDesc(Long userId);
}
