package com.findguni.model;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import java.util.UUID;

@Entity
@Table(name = "game_items", uniqueConstraints =
        @UniqueConstraint(name = "uk_item_game_key", columnNames = {"game_id", "stable_key"}),
        indexes = @Index(name = "idx_item_game", columnList = "game_id"))
public class GameItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private EscapeGame game;

    @Column(name = "stable_key", nullable = false, length = 36)
    private String stableKey;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 500)
    private String description = "";

    @Column(nullable = false, length = 16)
    private String emoji = "🗝️";

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    @ColumnDefault("'CUSTOM'")
    private ItemType itemType = ItemType.CUSTOM;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "clue_text", nullable = false, length = 2000)
    @ColumnDefault("''")
    private String clueText = "";

    @Column(name = "qr_enabled", nullable = false)
    @ColumnDefault("false")
    private boolean qrEnabled;

    public GameItem() {}

    public GameItem(EscapeGame game, String name) {
        this.game = game;
        this.name = name;
        this.stableKey = UUID.randomUUID().toString();
    }

    @PrePersist
    void ensureStableKey() {
        if (stableKey == null || stableKey.isBlank()) stableKey = UUID.randomUUID().toString();
    }

    public Long getId() { return id; }
    public EscapeGame getGame() { return game; }
    public String getStableKey() { return stableKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }
    public ItemType getItemType() { return itemType; }
    public void setItemType(ItemType itemType) { this.itemType = itemType; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getClueText() { return clueText; }
    public void setClueText(String clueText) { this.clueText = clueText; }
    public boolean isQrEnabled() { return qrEnabled; }
    public void setQrEnabled(boolean qrEnabled) { this.qrEnabled = qrEnabled; }
}
