package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.PartyMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartyMemberRepository extends JpaRepository<PartyMember, Long> {

    List<PartyMember> findByUserIdOrderBySlot(Long userId);

    List<PartyMember> findByUserIdAndActiveTrue(Long userId);

    long countByUserIdAndActiveTrue(Long userId);
}
