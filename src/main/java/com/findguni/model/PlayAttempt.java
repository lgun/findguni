package com.findguni.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "play_attempts", indexes = {
        @Index(name = "idx_attempt_session_time", columnList = "play_session_id,attempted_at"),
        @Index(name = "idx_attempt_stage", columnList = "stage_stable_key")
})
public class PlayAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "play_session_id", nullable = false)
    private PlaySession playSession;

    @Column(name = "stage_stable_key", nullable = false, length = 36)
    private String stageStableKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private AttemptKind kind;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt;

    protected PlayAttempt() {}

    public PlayAttempt(PlaySession playSession, String stageStableKey, AttemptKind kind, boolean success) {
        this.playSession = playSession;
        this.stageStableKey = stageStableKey;
        this.kind = kind;
        this.success = success;
        this.attemptedAt = Instant.now();
    }

    public Long getId() { return id; }
    public PlaySession getPlaySession() { return playSession; }
    public String getStageStableKey() { return stageStableKey; }
    public AttemptKind getKind() { return kind; }
    public boolean isSuccess() { return success; }
    public Instant getAttemptedAt() { return attemptedAt; }
}
