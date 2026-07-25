package com.findguni.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "server_heartbeats", indexes = {
        @Index(name = "idx_server_heartbeats_recorded_at", columnList = "recorded_at")
})
public class ServerHeartbeat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "server_name", nullable = false, length = 120)
    private String serverName = "default";

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected ServerHeartbeat() {}

    public ServerHeartbeat(String serverName) {
        this.serverName = (serverName == null || serverName.isBlank()) ? "default" : serverName;
        this.recordedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getServerName() { return serverName; }
    public Instant getRecordedAt() { return recordedAt; }
}
