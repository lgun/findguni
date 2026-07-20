package com.findguni.repository;

import com.findguni.model.GameStage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface GameStageRepository extends JpaRepository<GameStage, Long> {
    List<GameStage> findAllByGameIdOrderByPositionAsc(Long gameId);
    Optional<GameStage> findByIdAndGameId(Long id, Long gameId);
    Optional<GameStage> findByIdAndGameOwnerId(Long id, Long ownerId);
    long countByGameId(Long gameId);
}
