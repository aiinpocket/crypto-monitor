package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.PvpRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PvpRecordRepository extends JpaRepository<PvpRecord, Long> {

    /** 查詢用戶的最近 PVP 紀錄（作為挑戰者或被挑戰者） */
    @Query("""
        SELECT p FROM PvpRecord p
        WHERE p.attacker.id = :userId OR p.defender.id = :userId
        ORDER BY p.createdAt DESC
        LIMIT 20
    """)
    List<PvpRecord> findRecentByUserId(@Param("userId") Long userId);

    /** 統計用戶作為挑戰者的勝場 */
    @Query("SELECT COUNT(p) FROM PvpRecord p WHERE p.attacker.id = :userId AND p.attackerWon = true")
    long countAttackerWins(@Param("userId") Long userId);

    /** 統計用戶作為挑戰者的總場次 */
    long countByAttackerId(Long attackerId);

    /** 統計用戶作為被挑戰者且對方輸的場次（= 我方防守勝利） */
    @Query("SELECT COUNT(p) FROM PvpRecord p WHERE p.defender.id = :userId AND p.attackerWon = false")
    long countDefenderWins(@Param("userId") Long userId);

    /** 統計用戶作為被挑戰者的總場次 */
    long countByDefenderId(Long defenderId);
}
