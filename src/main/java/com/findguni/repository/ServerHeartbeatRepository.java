package com.findguni.repository;

import com.findguni.model.ServerHeartbeat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServerHeartbeatRepository extends JpaRepository<ServerHeartbeat, Long> {
}
