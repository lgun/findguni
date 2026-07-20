package com.findguni.repository;

import com.findguni.model.ScannedClue;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ScannedClueRepository extends JpaRepository<ScannedClue, Long> {
    Optional<ScannedClue> findByPlaySessionIdAndItemStableKey(Long playSessionId, String itemStableKey);
    List<ScannedClue> findAllByPlaySessionIdOrderByScannedAtAsc(Long playSessionId);
}
