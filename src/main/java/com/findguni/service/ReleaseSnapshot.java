package com.findguni.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import com.findguni.model.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ReleaseSnapshot(
        Long gameId,
        String slug,
        String title,
        String summary,
        String intro,
        String coverImageUrl,
        String accentColor,
        String secondaryColor,
        String backgroundColor,
        String gameIcon,
        boolean allowNotebook,
        boolean allowCluebook,
        boolean allowQrScanner,
        GameFlowMode flowMode,
        String bgmUrl,
        String bgmTitle,
        String bgmCreator,
        String bgmLicense,
        String bgmLicenseUrl,
        String bgmSourceUrl,
        double bgmVolume,
        boolean bgmLoop,
        int storyTextSpeed,
        boolean enableVignette,
        GameTheme theme,
        Difficulty difficulty,
        int estimatedMinutes,
        List<StageSnapshot> stages,
        List<ItemSnapshot> items,
        Boolean unlimitedHints,
        Integer hintLimit,
        Integer hintCooldownSeconds) {

    public Long getGameId() { return gameId; }
    public String getSlug() { return slug; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getIntro() { return intro; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public String getAccentColor() { return accentColor; }
    public String getSecondaryColor() { return secondaryColor; }
    public String getBackgroundColor() { return backgroundColor; }
    public String getGameIcon() { return gameIcon; }
    public boolean isAllowNotebook() { return allowNotebook; }
    public boolean isAllowCluebook() { return allowCluebook; }
    public boolean isAllowQrScanner() { return allowQrScanner; }
    public GameFlowMode getFlowMode() { return flowMode; }
    public String getBgmUrl() { return bgmUrl; }
    public String getBgmTitle() { return bgmTitle; }
    public String getBgmCreator() { return bgmCreator; }
    public String getBgmLicense() { return bgmLicense; }
    public String getBgmLicenseUrl() { return bgmLicenseUrl; }
    public String getBgmSourceUrl() { return bgmSourceUrl; }
    public double getBgmVolume() { return bgmVolume; }
    public boolean isBgmLoop() { return bgmLoop; }
    public int getStoryTextSpeed() { return storyTextSpeed; }
    public boolean isEnableVignette() { return enableVignette; }
    public GameTheme getTheme() { return theme; }
    public Difficulty getDifficulty() { return difficulty; }
    public int getEstimatedMinutes() { return estimatedMinutes; }
    public List<StageSnapshot> getStages() { return stages; }
    public List<ItemSnapshot> getItems() { return items; }
    public boolean isUnlimitedHints() { return unlimitedHints == null || unlimitedHints; }
    public int getHintLimit() { return hintLimit == null ? 3 : hintLimit; }
    public int getHintCooldownSeconds() { return hintCooldownSeconds == null ? 0 : hintCooldownSeconds; }

    public record StageSnapshot(
            String stableKey,
            int position,
            String title,
            String story,
            String instruction,
            String hint,
            PuzzleType puzzleType,
            String answerDigest,
            List<String> options,
            Map<String, OptionRoute> optionRoutes,
            int lockLength,
            String requiredItem,
            List<String> requiredItems,
            boolean consumeRequiredItems,
            String rewardItem,
            boolean qrEnabled,
            StageEntryMode entryMode,
            String nextStageKey,
            StoryEffect storyEffect,
            String sceneImageUrl,
            String sfxUrl,
            String sfxTitle,
            String sfxCreator,
            String sfxLicense,
            String sfxLicenseUrl,
            String sfxSourceUrl,
            double sfxVolume) {
        public String getStableKey() { return stableKey; }
        public int getPosition() { return position; }
        public String getTitle() { return title; }
        public String getStory() { return story; }
        public String getInstruction() { return instruction; }
        public String getHint() { return hint; }
        public PuzzleType getPuzzleType() { return puzzleType; }
        public String getAnswerDigest() { return answerDigest; }
        public List<String> getOptions() { return options; }
        public Map<String, OptionRoute> getOptionRoutes() { return optionRoutes == null ? Map.of() : optionRoutes; }
        public int getLockLength() { return lockLength; }
        public String getRequiredItem() { return requiredItem; }
        public List<String> getRequiredItems() {
            if (requiredItems != null && !requiredItems.isEmpty()) return requiredItems;
            return requiredItem == null || requiredItem.isBlank() ? List.of() : List.of(requiredItem);
        }
        public boolean isConsumeRequiredItems() { return consumeRequiredItems; }
        public String getRewardItem() { return rewardItem; }
        public boolean isQrEnabled() { return qrEnabled; }
        public StageEntryMode getEntryMode() { return entryMode; }
        public String getNextStageKey() { return nextStageKey; }
        public StoryEffect getStoryEffect() { return storyEffect; }
        public String getSceneImageUrl() { return sceneImageUrl; }
        public String getSfxUrl() { return sfxUrl; }
        public String getSfxTitle() { return sfxTitle; }
        public String getSfxCreator() { return sfxCreator; }
        public String getSfxLicense() { return sfxLicense; }
        public String getSfxLicenseUrl() { return sfxLicenseUrl; }
        public String getSfxSourceUrl() { return sfxSourceUrl; }
        public double getSfxVolume() { return sfxVolume; }
    }

    public static final class OptionRoute {
        private final String requiredItemKey;
        private final String ownedStageKey;
        private final String missingStageKey;

        public OptionRoute(String requiredItemKey, String ownedStageKey, String missingStageKey) {
            this.requiredItemKey = blankToNull(requiredItemKey);
            this.ownedStageKey = blankToNull(ownedStageKey);
            this.missingStageKey = blankToNull(missingStageKey);
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static OptionRoute from(JsonNode node) {
            if (node == null || node.isNull()) return new OptionRoute(null, null, null);
            if (node.isTextual()) return new OptionRoute(null, node.asText(), null);
            return new OptionRoute(text(node, "requiredItemKey"), text(node, "ownedStageKey"),
                    text(node, "missingStageKey"));
        }

        public String getRequiredItemKey() { return requiredItemKey; }
        public String getOwnedStageKey() { return ownedStageKey; }
        public String getMissingStageKey() { return missingStageKey; }

        public boolean conditional() { return requiredItemKey != null; }

        public boolean complete() {
            return conditional()
                    ? ownedStageKey != null && missingStageKey != null
                    : ownedStageKey != null;
        }

        public String resolve(Set<String> inventory) {
            if (!conditional()) return ownedStageKey;
            return inventory != null && inventory.contains(requiredItemKey) ? ownedStageKey : missingStageKey;
        }

        public List<String> targets() {
            if (ownedStageKey == null) {
                return missingStageKey == null ? List.of() : List.of(missingStageKey);
            }
            if (missingStageKey == null || ownedStageKey.equals(missingStageKey)) return List.of(ownedStageKey);
            return List.of(ownedStageKey, missingStageKey);
        }

        private static String text(JsonNode node, String field) {
            JsonNode value = node.get(field);
            return value == null ? null : value.asText(null);
        }

        private static String blankToNull(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }
    }

    public record ItemSnapshot(String stableKey, String name, String description, String emoji,
                               ItemType itemType, String imageUrl, String clueText, boolean qrEnabled,
                               boolean initiallyOwned, String copyableText,
                               String alternateRequiredItem, String alternateScanText) {
        public String getStableKey() { return stableKey; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getEmoji() { return emoji; }
        public ItemType getItemType() { return itemType; }
        public String getImageUrl() { return imageUrl; }
        public String getClueText() { return clueText; }
        public boolean isQrEnabled() { return qrEnabled; }
        public boolean isInitiallyOwned() { return initiallyOwned; }
        public String getCopyableText() { return copyableText; }
        public String getAlternateRequiredItem() { return alternateRequiredItem; }
        public String getAlternateScanText() { return alternateScanText; }
    }
}
