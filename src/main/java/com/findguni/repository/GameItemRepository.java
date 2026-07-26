package com.findguni.repository;

import com.findguni.model.GameItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface GameItemRepository extends JpaRepository<GameItem, Long> {
    List<GameItem> findAllByGameIdOrderByPositionAscIdAsc(Long gameId);
    Optional<GameItem> findByIdAndGameId(Long id, Long gameId);
    Optional<GameItem> findByIdAndGameOwnerId(Long id, Long ownerId);
}
