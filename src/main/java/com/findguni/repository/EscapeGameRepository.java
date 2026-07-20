package com.findguni.repository;

import com.findguni.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
import java.util.Optional;

public interface EscapeGameRepository extends JpaRepository<EscapeGame, Long> {
    Optional<EscapeGame> findByIdAndOwnerId(Long id, Long ownerId);
    Optional<EscapeGame> findBySlug(String slug);
    Optional<EscapeGame> findBySlugAndStatus(String slug, GameStatus status);
    boolean existsBySlug(String slug);
    @EntityGraph(attributePaths = "owner")
    List<EscapeGame> findAllByOwnerIdOrderByUpdatedAtDesc(Long ownerId);
    @EntityGraph(attributePaths = "owner")
    List<EscapeGame> findAllByVisibilityAndStatusOrderByUpdatedAtDesc(GameVisibility visibility, GameStatus status);
    @EntityGraph(attributePaths = "owner")
    List<EscapeGame> findAllByOrderByUpdatedAtDesc();
    long countByOwnerId(Long ownerId);
    long countByOwnerIdAndStatus(Long ownerId, GameStatus status);
    long countByStatus(GameStatus status);
}
