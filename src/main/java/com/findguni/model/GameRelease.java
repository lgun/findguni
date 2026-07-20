package com.findguni.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "game_releases", uniqueConstraints =
        @UniqueConstraint(name = "uk_release_game_version", columnNames = {"game_id", "version_number"}),
        indexes = @Index(name = "idx_release_game_version", columnList = "game_id,version_number"))
public class GameRelease {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false, updatable = false)
    private EscapeGame game;

    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;

    @Lob
    @Column(name = "snapshot_json", nullable = false, updatable = false, columnDefinition = "LONGTEXT")
    private String snapshotJson;

    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publishedAt;

    protected GameRelease() {}

    public GameRelease(EscapeGame game, int versionNumber, String snapshotJson) {
        this.game = game;
        this.versionNumber = versionNumber;
        this.snapshotJson = snapshotJson;
        this.publishedAt = Instant.now();
    }

    public Long getId() { return id; }
    public EscapeGame getGame() { return game; }
    public int getVersionNumber() { return versionNumber; }
    public String getSnapshotJson() { return snapshotJson; }
    public Instant getPublishedAt() { return publishedAt; }
}
