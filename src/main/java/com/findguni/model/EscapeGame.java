package com.findguni.model;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "escape_games", indexes = {
        @Index(name = "idx_game_owner_updated", columnList = "owner_id,updated_at"),
        @Index(name = "idx_game_discovery", columnList = "status,visibility")
})
public class EscapeGame {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserAccount owner;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 500)
    private String summary = "";

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String intro = "";

    @Column(name = "cover_image_url", length = 1000)
    private String coverImageUrl;

    @Column(name = "accent_color", nullable = false, length = 7)
    @ColumnDefault("'#8B5CF6'")
    private String accentColor = "#8B5CF6";

    @Column(name = "secondary_color", nullable = false, length = 7)
    @ColumnDefault("'#EC4899'")
    private String secondaryColor = "#EC4899";

    @Column(name = "background_color", nullable = false, length = 7)
    @ColumnDefault("'#0B1020'")
    private String backgroundColor = "#0B1020";

    @Column(name = "game_icon", nullable = false, length = 16)
    @ColumnDefault("'🔐'")
    private String gameIcon = "🔐";

    @Column(name = "allow_notebook", nullable = false)
    @ColumnDefault("true")
    private boolean allowNotebook = true;

    @Column(name = "allow_cluebook", nullable = false)
    @ColumnDefault("true")
    private boolean allowCluebook = true;

    @Column(name = "allow_qr_scanner", nullable = false)
    @ColumnDefault("true")
    private boolean allowQrScanner = true;

    @Column(name = "unlimited_hints", nullable = false)
    @ColumnDefault("true")
    private boolean unlimitedHints = true;

    @Column(name = "hint_limit", nullable = false)
    @ColumnDefault("3")
    private int hintLimit = 3;

    @Column(name = "hint_cooldown_seconds", nullable = false)
    @ColumnDefault("0")
    private int hintCooldownSeconds;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "flow_mode", nullable = false, length = 24)
    @ColumnDefault("'QR_EXPLORATION'")
    private GameFlowMode flowMode = GameFlowMode.QR_EXPLORATION;

    @Column(name = "bgm_url", length = 1000)
    private String bgmUrl;

    @Column(name = "bgm_title", length = 200)
    private String bgmTitle;

    @Column(name = "bgm_creator", length = 200)
    private String bgmCreator;

    @Column(name = "bgm_license", length = 100)
    private String bgmLicense;

    @Column(name = "bgm_license_url", length = 1000)
    private String bgmLicenseUrl;

    @Column(name = "bgm_source_url", length = 1000)
    private String bgmSourceUrl;

    @Column(name = "bgm_volume", nullable = false)
    @ColumnDefault("0.55")
    private double bgmVolume = 0.55;

    @Column(name = "bgm_loop", nullable = false)
    @ColumnDefault("true")
    private boolean bgmLoop = true;

    @Column(name = "story_text_speed", nullable = false)
    @ColumnDefault("32")
    private int storyTextSpeed = 32;

    @Column(name = "enable_vignette", nullable = false)
    @ColumnDefault("true")
    private boolean enableVignette = true;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private GameTheme theme = GameTheme.MIDNIGHT;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Difficulty difficulty = Difficulty.NORMAL;

    @Column(name = "estimated_minutes", nullable = false)
    private int estimatedMinutes = 30;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private GameVisibility visibility = GameVisibility.LINK_ONLY;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private GameStatus status = GameStatus.DRAFT;

    @Column(name = "published_version", nullable = false)
    private int publishedVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long entityVersion;

    @Transient
    private long stageCount;
    @Transient
    private long playCount;
    @Transient
    private double completionRate;

    public EscapeGame() {}

    public EscapeGame(UserAccount owner, String slug, String title) {
        this.owner = owner;
        this.slug = slug;
        this.title = title;
    }

    @PrePersist
    void created() { createdAt = updatedAt = Instant.now(); }

    @PreUpdate
    void updated() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public UserAccount getOwner() { return owner; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public void setCoverImageUrl(String coverImageUrl) { this.coverImageUrl = coverImageUrl; }
    public String getAccentColor() { return accentColor; }
    public void setAccentColor(String accentColor) { this.accentColor = accentColor; }
    public String getSecondaryColor() { return secondaryColor; }
    public void setSecondaryColor(String secondaryColor) { this.secondaryColor = secondaryColor; }
    public String getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(String backgroundColor) { this.backgroundColor = backgroundColor; }
    public String getGameIcon() { return gameIcon; }
    public void setGameIcon(String gameIcon) { this.gameIcon = gameIcon; }
    public boolean isAllowNotebook() { return allowNotebook; }
    public void setAllowNotebook(boolean allowNotebook) { this.allowNotebook = allowNotebook; }
    public boolean isAllowCluebook() { return allowCluebook; }
    public void setAllowCluebook(boolean allowCluebook) { this.allowCluebook = allowCluebook; }
    public boolean isAllowQrScanner() { return allowQrScanner; }
    public void setAllowQrScanner(boolean allowQrScanner) { this.allowQrScanner = allowQrScanner; }
    public boolean isUnlimitedHints() { return unlimitedHints; }
    public void setUnlimitedHints(boolean unlimitedHints) { this.unlimitedHints = unlimitedHints; }
    public int getHintLimit() { return hintLimit; }
    public void setHintLimit(int hintLimit) { this.hintLimit = hintLimit; }
    public int getHintCooldownSeconds() { return hintCooldownSeconds; }
    public void setHintCooldownSeconds(int hintCooldownSeconds) { this.hintCooldownSeconds = hintCooldownSeconds; }
    public GameFlowMode getFlowMode() { return flowMode; }
    public void setFlowMode(GameFlowMode flowMode) { this.flowMode = flowMode; }
    public String getBgmUrl() { return bgmUrl; }
    public void setBgmUrl(String bgmUrl) { this.bgmUrl = bgmUrl; }
    public String getBgmTitle() { return bgmTitle; }
    public void setBgmTitle(String bgmTitle) { this.bgmTitle = bgmTitle; }
    public String getBgmCreator() { return bgmCreator; }
    public void setBgmCreator(String bgmCreator) { this.bgmCreator = bgmCreator; }
    public String getBgmLicense() { return bgmLicense; }
    public void setBgmLicense(String bgmLicense) { this.bgmLicense = bgmLicense; }
    public String getBgmLicenseUrl() { return bgmLicenseUrl; }
    public void setBgmLicenseUrl(String bgmLicenseUrl) { this.bgmLicenseUrl = bgmLicenseUrl; }
    public String getBgmSourceUrl() { return bgmSourceUrl; }
    public void setBgmSourceUrl(String bgmSourceUrl) { this.bgmSourceUrl = bgmSourceUrl; }
    public double getBgmVolume() { return bgmVolume; }
    public void setBgmVolume(double bgmVolume) { this.bgmVolume = bgmVolume; }
    public boolean isBgmLoop() { return bgmLoop; }
    public void setBgmLoop(boolean bgmLoop) { this.bgmLoop = bgmLoop; }
    public int getStoryTextSpeed() { return storyTextSpeed; }
    public void setStoryTextSpeed(int storyTextSpeed) { this.storyTextSpeed = storyTextSpeed; }
    public boolean isEnableVignette() { return enableVignette; }
    public void setEnableVignette(boolean enableVignette) { this.enableVignette = enableVignette; }
    public GameTheme getTheme() { return theme; }
    public void setTheme(GameTheme theme) { this.theme = theme; }
    public Difficulty getDifficulty() { return difficulty; }
    public void setDifficulty(Difficulty difficulty) { this.difficulty = difficulty; }
    public int getEstimatedMinutes() { return estimatedMinutes; }
    public void setEstimatedMinutes(int estimatedMinutes) { this.estimatedMinutes = estimatedMinutes; }
    public GameVisibility getVisibility() { return visibility; }
    public void setVisibility(GameVisibility visibility) { this.visibility = visibility; }
    public GameStatus getStatus() { return status; }
    public void setStatus(GameStatus status) { this.status = status; }
    public int getPublishedVersion() { return publishedVersion; }
    public void setPublishedVersion(int publishedVersion) { this.publishedVersion = publishedVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public boolean isPublished() { return status == GameStatus.PUBLISHED && publishedVersion > 0; }
    public long getStageCount() { return stageCount; }
    public void setStageCount(long stageCount) { this.stageCount = stageCount; }
    public long getPlayCount() { return playCount; }
    public void setPlayCount(long playCount) { this.playCount = playCount; }
    public double getCompletionRate() { return completionRate; }
    public void setCompletionRate(double completionRate) { this.completionRate = completionRate; }
}
