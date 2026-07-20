package com.findguni.repository;

import com.findguni.model.PlaySession;
import com.findguni.model.PlayStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PlaySessionRepository extends JpaRepository<PlaySession, Long> {
    Optional<PlaySession> findFirstByDeviceTokenHashAndRelease_Game_IdAndStatusOrderByLastActivityAtDesc(
            String deviceTokenHash, Long gameId, PlayStatus status);
    Optional<PlaySession> findFirstByDeviceTokenHashAndRelease_Game_IdOrderByLastActivityAtDesc(
            String deviceTokenHash, Long gameId);
    long countByRelease_Game_Id(Long gameId);
    long countByRelease_Game_IdAndStatus(Long gameId, PlayStatus status);
    long countByStatus(PlayStatus status);
}
