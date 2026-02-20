package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.MonsterDrop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonsterDropRepository extends JpaRepository<MonsterDrop, Long> {

    List<MonsterDrop> findByMonsterId(Long monsterId);
}
