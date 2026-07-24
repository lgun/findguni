package com.findguni.model;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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
    @JdbcTypeCode(SqlTypes.VARCHAR)
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

    @Column(name = "initially_owned", nullable = false)
    @ColumnDefault("false")
    private boolean initiallyOwned;

    @Column(name = "copyable_text", length = 1000)
    private String copyableText;

    @Column(name = "alternate_required_item", length = 36)
    private String alternateRequiredItem;

    @Lob
    @Column(name = "alternate_scan_text", columnDefinition = "LONGTEXT")
    private String alternateScanText;

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
    public boolean isInitiallyOwned() { return initiallyOwned; }
    public void setInitiallyOwned(boolean initiallyOwned) { this.initiallyOwned = initiallyOwned; }
    public String getCopyableText() { return copyableText; }
    public void setCopyableText(String copyableText) { this.copyableText = copyableText; }
    public String getAlternateRequiredItem() { return alternateRequiredItem; }
    public void setAlternateRequiredItem(String alternateRequiredItem) {
        this.alternateRequiredItem = alternateRequiredItem;
    }
    public String getAlternateScanText() { return alternateScanText; }
    public void setAlternateScanText(String alternateScanText) { this.alternateScanText = alternateScanText; }
}
