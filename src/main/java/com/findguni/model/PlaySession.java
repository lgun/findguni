package com.findguni.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "play_sessions", indexes = {
        @Index(name = "idx_play_device_status", columnList = "device_token_hash,status,last_activity_at"),
        @Index(name = "idx_play_release", columnList = "release_id")
})
public class PlaySession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_token_hash", nullable = false, length = 64)
    private String deviceTokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "release_id", nullable = false)
    private GameRelease release;

    @Column(name = "progress_index", nullable = false)
    private int progressIndex;

    @Lob
    @Column(name = "inventory_json", nullable = false, columnDefinition = "LONGTEXT")
    private String inventoryJson = "[]";

    @Lob
    @Column(name = "consumed_items_json", columnDefinition = "LONGTEXT")
    private String consumedItemsJson = "[]";

    @Lob
    @Column(name = "discovered_stages_json", columnDefinition = "LONGTEXT")
    private String discoveredStagesJson = "[]";

    @Lob
    @Column(name = "solved_stages_json", columnDefinition = "LONGTEXT")
    private String solvedStagesJson = "[]";

    @Column(name = "active_stage_key", length = 36)
    private String activeStageKey;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PlayStatus status = PlayStatus.ACTIVE;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "hints_used", nullable = false)
    private int hintsUsed;

    @Lob
    @Column(name = "revealed_hints_json", columnDefinition = "LONGTEXT")
    private String revealedHintsJson = "[]";

    @Column(name = "last_hint_at")
    private Instant lastHintAt;

    @Version
    private long entityVersion;

    protected PlaySession() {}

    public PlaySession(String deviceTokenHash, GameRelease release) {
        this.deviceTokenHash = deviceTokenHash;
        this.release = release;
        this.startedAt = this.lastActivityAt = Instant.now();
    }

    public void touch() { this.lastActivityAt = Instant.now(); }

    public void advance() {
        progressIndex++;
        attemptCount++;
        touch();
    }

    public void recordFailedAttempt() { attemptCount++; touch(); }

    public void recordSuccessfulAttempt() { attemptCount++; touch(); }

    public void recordHint(String revealedHintsJson) {
        hintsUsed++;
        this.revealedHintsJson = revealedHintsJson;
        lastHintAt = Instant.now();
        touch();
    }

    public void complete() {
        status = PlayStatus.COMPLETED;
        completedAt = Instant.now();
        touch();
    }

    public void abandon() { status = PlayStatus.ABANDONED; touch(); }

    public Long getId() { return id; }
    public String getDeviceTokenHash() { return deviceTokenHash; }
    public GameRelease getRelease() { return release; }
    public int getProgressIndex() { return progressIndex; }
    public String getInventoryJson() { return inventoryJson; }
    public void setInventoryJson(String inventoryJson) { this.inventoryJson = inventoryJson; }
    public String getConsumedItemsJson() { return consumedItemsJson; }
    public void setConsumedItemsJson(String consumedItemsJson) { this.consumedItemsJson = consumedItemsJson; }
    public String getDiscoveredStagesJson() { return discoveredStagesJson; }
    public void setDiscoveredStagesJson(String discoveredStagesJson) { this.discoveredStagesJson = discoveredStagesJson; }
    public String getSolvedStagesJson() { return solvedStagesJson; }
    public void setSolvedStagesJson(String solvedStagesJson) { this.solvedStagesJson = solvedStagesJson; }
    public String getActiveStageKey() { return activeStageKey; }
    public void setActiveStageKey(String activeStageKey) { this.activeStageKey = activeStageKey; touch(); }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; touch(); }
    public PlayStatus getStatus() { return status; }
    public void setStatus(PlayStatus status) { this.status = status; touch(); }
    public void setProgressIndex(int progressIndex) { this.progressIndex = progressIndex; touch(); }
    public void setRelease(GameRelease release) { this.release = release; touch(); }
    public void setRevealedHintsJson(String revealedHintsJson) { this.revealedHintsJson = revealedHintsJson; touch(); }
    public Instant getStartedAt() { return startedAt; }
    public Instant getLastActivityAt() { return lastActivityAt; }
    public Instant getCompletedAt() { return completedAt; }
    public int getAttemptCount() { return attemptCount; }
    public int getHintsUsed() { return hintsUsed; }
    public String getRevealedHintsJson() { return revealedHintsJson; }
    public Instant getLastHintAt() { return lastHintAt; }
    public boolean isActive() { return status == PlayStatus.ACTIVE; }
    public boolean isCompleted() { return status == PlayStatus.COMPLETED; }
}
