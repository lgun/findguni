package com.findguni.model;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "game_stages", uniqueConstraints = {
        @UniqueConstraint(name = "uk_stage_game_key", columnNames = {"game_id", "stable_key"})
}, indexes = @Index(name = "idx_stage_game_position", columnList = "game_id,position"))
public class GameStage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private EscapeGame game;

    @Column(name = "stable_key", nullable = false, length = 36)
    private String stableKey;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 120)
    private String title;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String story = "";

    @Column(nullable = false, length = 500)
    private String instruction = "";

    @Column(nullable = false, length = 500)
    private String hint = "";

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "puzzle_type", nullable = false, length = 30)
    private PuzzleType puzzleType = PuzzleType.STORY;

    @Lob
    @Column(name = "draft_answer", columnDefinition = "LONGTEXT")
    private String draftAnswer;

    @Lob
    @Column(name = "options_text", columnDefinition = "LONGTEXT")
    private String optionsText;

    @Lob
    @Column(name = "option_routes_json", columnDefinition = "LONGTEXT")
    private String optionRoutesJson;

    @Column(name = "lock_length", nullable = false)
    private int lockLength = 4;

    @Column(name = "required_item", length = 36)
    private String requiredItem;

    @Lob
    @Column(name = "required_items", columnDefinition = "LONGTEXT")
    private String requiredItems;

    @Column(name = "consume_required_items", nullable = false)
    @ColumnDefault("false")
    private boolean consumeRequiredItems;

    @Column(name = "reward_item", length = 36)
    private String rewardItem;

    @Column(name = "qr_enabled", nullable = false)
    @ColumnDefault("true")
    private boolean qrEnabled = true;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "entry_mode", nullable = false, length = 16)
    @ColumnDefault("'QR'")
    private StageEntryMode entryMode = StageEntryMode.QR;

    @Column(name = "next_stage_key", length = 36)
    private String nextStageKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "story_effect", nullable = false, length = 20)
    @ColumnDefault("'FADE'")
    private StoryEffect storyEffect = StoryEffect.FADE;

    @Column(name = "scene_image_url", length = 1000)
    private String sceneImageUrl;

    @Column(name = "sfx_url", length = 1000)
    private String sfxUrl;

    @Column(name = "sfx_title", length = 200)
    private String sfxTitle;

    @Column(name = "sfx_creator", length = 200)
    private String sfxCreator;

    @Column(name = "sfx_license", length = 100)
    private String sfxLicense;

    @Column(name = "sfx_license_url", length = 1000)
    private String sfxLicenseUrl;

    @Column(name = "sfx_source_url", length = 1000)
    private String sfxSourceUrl;

    @Column(name = "sfx_volume", nullable = false)
    @ColumnDefault("0.8")
    private double sfxVolume = 0.8;

    @Version
    private long entityVersion;

    public GameStage() {}

    public GameStage(EscapeGame game, int position, String title) {
        this.game = game;
        this.position = position;
        this.title = title;
        this.stableKey = UUID.randomUUID().toString();
    }

    @PrePersist
    void ensureStableKey() {
        if (stableKey == null || stableKey.isBlank()) stableKey = UUID.randomUUID().toString();
    }

    public Long getId() { return id; }
    public EscapeGame getGame() { return game; }
    public String getStableKey() { return stableKey; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStory() { return story; }
    public void setStory(String story) { this.story = story; }
    public String getInstruction() { return instruction; }
    public void setInstruction(String instruction) { this.instruction = instruction; }
    public String getHint() { return hint; }
    public void setHint(String hint) { this.hint = hint; }
    public PuzzleType getPuzzleType() { return puzzleType; }
    public void setPuzzleType(PuzzleType puzzleType) { this.puzzleType = puzzleType; }
    public String getDraftAnswer() { return draftAnswer; }
    public void setDraftAnswer(String draftAnswer) { this.draftAnswer = draftAnswer; }
    public String getOptionsText() { return optionsText; }
    public void setOptionsText(String optionsText) { this.optionsText = optionsText; }
    public String getOptionRoutesJson() { return optionRoutesJson; }
    public void setOptionRoutesJson(String optionRoutesJson) { this.optionRoutesJson = optionRoutesJson; }
    public int getLockLength() { return lockLength; }
    public void setLockLength(int lockLength) { this.lockLength = lockLength; }
    public String getRequiredItem() { return requiredItem; }
    public void setRequiredItem(String requiredItem) {
        this.requiredItem = requiredItem;
        this.requiredItems = requiredItem;
    }
    public List<String> getRequiredItems() {
        String source = requiredItems == null || requiredItems.isBlank() ? requiredItem : requiredItems;
        if (source == null || source.isBlank()) return List.of();
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String value : source.replace("\r", "").split("[\\n,]")) {
            if (!value.isBlank()) values.add(value.trim());
        }
        return List.copyOf(values);
    }
    public void setRequiredItems(Collection<String> values) {
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        if (values != null) values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .forEach(cleaned::add);
        this.requiredItems = cleaned.isEmpty() ? null : String.join("\n", cleaned);
        this.requiredItem = cleaned.stream().findFirst().orElse(null);
    }
    public boolean isConsumeRequiredItems() { return consumeRequiredItems; }
    public void setConsumeRequiredItems(boolean consumeRequiredItems) {
        this.consumeRequiredItems = consumeRequiredItems;
    }
    public String getRewardItem() { return rewardItem; }
    public void setRewardItem(String rewardItem) { this.rewardItem = rewardItem; }
    public boolean isQrEnabled() { return entryMode == null ? qrEnabled : entryMode == StageEntryMode.QR; }
    public void setQrEnabled(boolean qrEnabled) {
        this.qrEnabled = qrEnabled;
        if (entryMode != StageEntryMode.LINKED) entryMode = qrEnabled ? StageEntryMode.QR : StageEntryMode.START;
    }
    public StageEntryMode getEntryMode() { return entryMode == null ? (qrEnabled ? StageEntryMode.QR : StageEntryMode.START) : entryMode; }
    public void setEntryMode(StageEntryMode entryMode) {
        this.entryMode = entryMode == null ? StageEntryMode.QR : entryMode;
        this.qrEnabled = this.entryMode == StageEntryMode.QR;
    }
    public String getNextStageKey() { return nextStageKey; }
    public void setNextStageKey(String nextStageKey) { this.nextStageKey = nextStageKey; }
    public StoryEffect getStoryEffect() { return storyEffect; }
    public void setStoryEffect(StoryEffect storyEffect) { this.storyEffect = storyEffect; }
    public String getSceneImageUrl() { return sceneImageUrl; }
    public void setSceneImageUrl(String sceneImageUrl) { this.sceneImageUrl = sceneImageUrl; }
    public String getSfxUrl() { return sfxUrl; }
    public void setSfxUrl(String sfxUrl) { this.sfxUrl = sfxUrl; }
    public String getSfxTitle() { return sfxTitle; }
    public void setSfxTitle(String sfxTitle) { this.sfxTitle = sfxTitle; }
    public String getSfxCreator() { return sfxCreator; }
    public void setSfxCreator(String sfxCreator) { this.sfxCreator = sfxCreator; }
    public String getSfxLicense() { return sfxLicense; }
    public void setSfxLicense(String sfxLicense) { this.sfxLicense = sfxLicense; }
    public String getSfxLicenseUrl() { return sfxLicenseUrl; }
    public void setSfxLicenseUrl(String sfxLicenseUrl) { this.sfxLicenseUrl = sfxLicenseUrl; }
    public String getSfxSourceUrl() { return sfxSourceUrl; }
    public void setSfxSourceUrl(String sfxSourceUrl) { this.sfxSourceUrl = sfxSourceUrl; }
    public double getSfxVolume() { return sfxVolume; }
    public void setSfxVolume(double sfxVolume) { this.sfxVolume = sfxVolume; }
}
