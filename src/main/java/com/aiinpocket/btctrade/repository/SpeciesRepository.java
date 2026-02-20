package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.Species;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpeciesRepository extends JpaRepository<Species, Long> {

    Optional<Species> findByName(String name);

    List<Species> findByUnlockLevelLessThanEqual(Integer level);
}
