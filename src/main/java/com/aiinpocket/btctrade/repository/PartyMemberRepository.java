package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.PartyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PartyMemberRepository extends JpaRepository<PartyMember, Long> {

    @Query("SELECT m FROM PartyMember m JOIN FETCH m.species WHERE m.user.id = :userId ORDER BY m.slot")
    List<PartyMember> findByUserIdOrderBySlot(Long userId);

    @Query("SELECT m FROM PartyMember m JOIN FETCH m.species WHERE m.user.id = :userId AND m.active = true")
    List<PartyMember> findByUserIdAndActiveTrue(Long userId);

    long countByUserIdAndActiveTrue(Long userId);
}
