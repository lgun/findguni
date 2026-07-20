package com.findguni.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "scanned_clues", uniqueConstraints =
        @UniqueConstraint(name = "uk_scanned_clue_session_item", columnNames = {"play_session_id", "item_stable_key"}),
        indexes = @Index(name = "idx_scanned_clue_session_time", columnList = "play_session_id,scanned_at"))
public class ScannedClue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "play_session_id", nullable = false)
    private PlaySession playSession;

    @Column(name = "item_stable_key", nullable = false, length = 36)
    private String itemStableKey;

    @Column(name = "scanned_at", nullable = false, updatable = false)
    private Instant scannedAt;

    protected ScannedClue() {}

    public ScannedClue(PlaySession playSession, String itemStableKey) {
        this.playSession = playSession;
        this.itemStableKey = itemStableKey;
        this.scannedAt = Instant.now();
    }

    public Long getId() { return id; }
    public PlaySession getPlaySession() { return playSession; }
    public String getItemStableKey() { return itemStableKey; }
    public Instant getScannedAt() { return scannedAt; }
}
