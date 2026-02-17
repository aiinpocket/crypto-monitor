package com.aiinpocket.btctrade.repository;

import com.aiinpocket.btctrade.model.entity.TrackedSymbol;
import com.aiinpocket.btctrade.model.enums.SyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrackedSymbolRepository extends JpaRepository<TrackedSymbol, Long> {

    List<TrackedSymbol> findByActiveTrue();

    List<TrackedSymbol> findByActiveTrueAndSyncStatus(SyncStatus syncStatus);

    Optional<TrackedSymbol> findBySymbol(String symbol);

    boolean existsBySymbol(String symbol);

    List<TrackedSymbol> findByActiveTrueAndSyncStatusIn(List<SyncStatus> statuses);

    List<TrackedSymbol> findByActiveTrueAndSyncStatusNot(SyncStatus status);
}
