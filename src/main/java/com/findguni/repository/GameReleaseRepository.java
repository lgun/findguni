package com.findguni.repository;

import com.findguni.model.GameRelease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
import java.util.Optional;

public interface GameReleaseRepository extends JpaRepository<GameRelease, Long> {
    Optional<GameRelease> findByGameIdAndVersionNumber(Long gameId, int versionNumber);
    Optional<GameRelease> findFirstByGameIdOrderByVersionNumberDesc(Long gameId);
    @EntityGraph(attributePaths = {"game", "game.owner"})
    List<GameRelease> findTop20ByOrderByPublishedAtDesc();
}
