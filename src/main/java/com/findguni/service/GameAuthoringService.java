package com.findguni.service;

import com.findguni.model.*;
import com.findguni.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.text.Normalizer;
import java.util.*;

@Service
public class GameAuthoringService {
    private static final String UUID_PATH = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    private final EscapeGameRepository games;
    private final GameStageRepository stages;
    private final GameItemRepository items;
    private final PlaySessionRepository plays;

    public GameAuthoringService(EscapeGameRepository games, GameStageRepository stages,
                                GameItemRepository items, PlaySessionRepository plays) {
        this.games = games;
        this.stages = stages;
        this.items = items;
        this.plays = plays;
    }

    @Transactional(readOnly = true)
    public List<EscapeGame> ownedGames(UserAccount owner) {
        return enrich(games.findAllByOwnerIdOrderByUpdatedAtDesc(owner.getId()));
    }

    @Transactional(readOnly = true)
    public EscapeGame ownedGame(Long gameId, UserAccount owner) {
        return games.findByIdAndOwnerId(gameId, owner.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<GameStage> stages(Long gameId, UserAccount owner) {
        ownedGame(gameId, owner);
        return stages.findAllByGameIdOrderByPositionAsc(gameId);
    }

    @Transactional(readOnly = true)
    public List<GameItem> items(Long gameId, UserAccount owner) {
        ownedGame(gameId, owner);
        return items.findAllByGameIdOrderByIdAsc(gameId);
    }

    @Transactional
    public EscapeGame create(UserAccount owner, String title, String slug, String template,
                             GameTheme theme, Difficulty difficulty, int estimatedMinutes) {
        return create(owner, title, slug, template, theme, difficulty, estimatedMinutes, GameFlowMode.LINEAR);
    }

    @Transactional
    public EscapeGame create(UserAccount owner, String title, String slug, String template,
                             GameTheme theme, Difficulty difficulty, int estimatedMinutes,
                             GameFlowMode flowMode) {
        String safeTitle = required(title, "게임 제목", 120);
        EscapeGame game = new EscapeGame(owner, uniqueSlug(slug, safeTitle, null), safeTitle);
        game.setTheme(theme == null ? GameTheme.MIDNIGHT : theme);
        game.setDifficulty(difficulty == null ? Difficulty.NORMAL : difficulty);
        game.setEstimatedMinutes(clamp(estimatedMinutes, 5, 240));
        game.setFlowMode(flowMode == null ? GameFlowMode.QR_EXPLORATION : flowMode);
        games.save(game);
        applyTemplate(game, template);
        return game;
    }

    @Transactional
    public EscapeGame updateFlowMode(Long gameId, UserAccount owner, GameFlowMode flowMode) {
        EscapeGame game = ownedGame(gameId, owner);
        game.setFlowMode(flowMode == null ? GameFlowMode.QR_EXPLORATION : flowMode);
        if (game.getFlowMode() == GameFlowMode.QR_EXPLORATION) game.setAllowQrScanner(true);
        return game;
    }

    @Transactional
    public EscapeGame updateHintPolicy(Long gameId, UserAccount owner, boolean unlimitedHints,
                                       int hintLimit, int hintCooldownSeconds) {
        EscapeGame game = ownedGame(gameId, owner);
        game.setUnlimitedHints(unlimitedHints);
        game.setHintLimit(validatedRange(hintLimit, 1, 100, "힌트 횟수 제한"));
        game.setHintCooldownSeconds(validatedRange(hintCooldownSeconds, 0, 3_600, "힌트 대기시간"));
        return game;
    }

    @Transactional
    public EscapeGame updateSettings(Long gameId, UserAccount owner, String title, String slug,
                                     String summary, String intro, String coverImageUrl, String accentColor,
                                     GameTheme theme, Difficulty difficulty,
                                     int estimatedMinutes, GameVisibility visibility) {
        EscapeGame game = ownedGame(gameId, owner);
        return updateSettings(gameId, owner, title, slug, summary, intro, coverImageUrl, accentColor,
                game.getSecondaryColor(), game.getBackgroundColor(), game.getGameIcon(),
                game.isAllowNotebook(), game.isAllowCluebook(), game.isAllowQrScanner(),
                theme, difficulty, estimatedMinutes, visibility);
    }

    @Transactional
    public EscapeGame updateSettings(Long gameId, UserAccount owner, String title, String slug,
                                     String summary, String intro, String coverImageUrl, String accentColor,
                                     String secondaryColor, String backgroundColor, String gameIcon,
                                     boolean allowNotebook, boolean allowCluebook, boolean allowQrScanner,
                                     GameTheme theme, Difficulty difficulty,
                                     int estimatedMinutes, GameVisibility visibility) {
        EscapeGame game = ownedGame(gameId, owner);
        return updateSettings(gameId, owner, title, slug, summary, intro, coverImageUrl, accentColor,
                secondaryColor, backgroundColor, gameIcon, allowNotebook, allowCluebook, allowQrScanner,
                theme, difficulty, estimatedMinutes, visibility,
                game.getBgmUrl(), game.getBgmTitle(), game.getBgmCreator(), game.getBgmLicense(),
                game.getBgmLicenseUrl(), game.getBgmSourceUrl(), game.getBgmVolume(), game.isBgmLoop(),
                game.getStoryTextSpeed(), game.isEnableVignette());
    }

    @Transactional
    public EscapeGame updateSettings(Long gameId, UserAccount owner, String title, String slug,
                                     String summary, String intro, String coverImageUrl, String accentColor,
                                     String secondaryColor, String backgroundColor, String gameIcon,
                                     boolean allowNotebook, boolean allowCluebook, boolean allowQrScanner,
                                     GameTheme theme, Difficulty difficulty,
                                     int estimatedMinutes, GameVisibility visibility,
                                     String bgmUrl, String bgmTitle, String bgmCreator, String bgmLicense,
                                     String bgmLicenseUrl, String bgmSourceUrl, double bgmVolume, boolean bgmLoop,
                                     int storyTextSpeed, boolean enableVignette) {
        EscapeGame game = ownedGame(gameId, owner);
        return updateSettings(gameId, owner, title, slug, summary, intro, coverImageUrl, accentColor,
                secondaryColor, backgroundColor, gameIcon, allowNotebook, allowCluebook, allowQrScanner,
                theme, difficulty, estimatedMinutes, visibility, bgmUrl, bgmTitle, bgmCreator, bgmLicense,
                bgmLicenseUrl, bgmSourceUrl, bgmVolume, bgmLoop, storyTextSpeed, enableVignette,
                game.isUnlimitedHints(), game.getHintLimit(), game.getHintCooldownSeconds());
    }

    @Transactional
    public EscapeGame updateSettings(Long gameId, UserAccount owner, String title, String slug,
                                     String summary, String intro, String coverImageUrl, String accentColor,
                                     String secondaryColor, String backgroundColor, String gameIcon,
                                     boolean allowNotebook, boolean allowCluebook, boolean allowQrScanner,
                                     GameTheme theme, Difficulty difficulty,
                                     int estimatedMinutes, GameVisibility visibility,
                                     String bgmUrl, String bgmTitle, String bgmCreator, String bgmLicense,
                                     String bgmLicenseUrl, String bgmSourceUrl, double bgmVolume, boolean bgmLoop,
                                     int storyTextSpeed, boolean enableVignette,
                                     boolean unlimitedHints, int hintLimit, int hintCooldownSeconds) {
        EscapeGame game = ownedGame(gameId, owner);
        game.setTitle(required(title, "게임 제목", 120));
        if (game.getPublishedVersion() == 0 || Objects.equals(game.getSlug(), slugify(slug))) {
            game.setSlug(uniqueSlug(slug, game.getTitle(), game.getId()));
        }
        game.setSummary(optional(summary, 500));
        game.setIntro(optional(intro, 20_000));
        game.setCoverImageUrl(validatedImageUrl(coverImageUrl));
        game.setAccentColor(validatedAccent(accentColor));
        game.setSecondaryColor(validatedColor(secondaryColor, "보조 색상", "#EC4899"));
        game.setBackgroundColor(validatedColor(backgroundColor, "배경 색상", "#0B1020"));
        game.setGameIcon(validatedIcon(gameIcon));
        game.setAllowNotebook(allowNotebook);
        game.setAllowCluebook(allowCluebook);
        game.setAllowQrScanner(allowQrScanner);
        game.setUnlimitedHints(unlimitedHints);
        game.setHintLimit(validatedRange(hintLimit, 1, 100, "힌트 횟수 제한"));
        game.setHintCooldownSeconds(validatedRange(hintCooldownSeconds, 0, 3_600, "힌트 대기시간"));
        game.setBgmUrl(validatedAudioUrl(bgmUrl, "배경 음악"));
        game.setBgmTitle(optionalOrNull(bgmTitle, 200));
        game.setBgmCreator(optionalOrNull(bgmCreator, 200));
        game.setBgmLicense(optionalOrNull(bgmLicense, 100));
        game.setBgmLicenseUrl(validatedHttpsUrl(bgmLicenseUrl, "배경 음악 라이선스 URL"));
        game.setBgmSourceUrl(validatedHttpsUrl(bgmSourceUrl, "배경 음악 출처 URL"));
        game.setBgmVolume(validatedVolume(bgmVolume, "배경 음악 볼륨"));
        game.setBgmLoop(bgmLoop);
        game.setStoryTextSpeed(validatedRange(storyTextSpeed, 10, 100, "스토리 글자 속도"));
        game.setEnableVignette(enableVignette);
        game.setTheme(theme == null ? GameTheme.MIDNIGHT : theme);
        game.setDifficulty(difficulty == null ? Difficulty.NORMAL : difficulty);
        game.setEstimatedMinutes(clamp(estimatedMinutes, 5, 240));
        game.setVisibility(visibility == null ? GameVisibility.LINK_ONLY : visibility);
        return game;
    }

    @Transactional
    public GameStage addStage(Long gameId, UserAccount owner, StageDraft draft) {
        EscapeGame game = ownedGame(gameId, owner);
        int nextPosition = stages.findAllByGameIdOrderByPositionAsc(gameId).size();
        return stages.save(apply(new GameStage(game, nextPosition, required(draft.title(), "스테이지 제목", 120)), draft));
    }

    @Transactional
    public GameStage addStage(Long gameId, UserAccount owner, StageDraft draft,
                              StageEntryMode entryMode, String nextStageKey) {
        GameStage stage = addStage(gameId, owner, draft);
        return applyStageFlow(stage, gameId, entryMode, nextStageKey);
    }

    @Transactional
    public GameStage updateStage(Long stageId, UserAccount owner, StageDraft draft) {
        GameStage stage = stages.findByIdAndGameOwnerId(stageId, owner.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return apply(stage, draft);
    }

    @Transactional
    public GameStage updateStage(Long stageId, UserAccount owner, StageDraft draft,
                                 StageEntryMode entryMode, String nextStageKey) {
        GameStage stage = updateStage(stageId, owner, draft);
        return applyStageFlow(stage, stage.getGame().getId(), entryMode, nextStageKey);
    }

    @Transactional
    public GameStage updateStage(Long gameId, Long stageId, UserAccount owner, StageDraft draft) {
        ownedGame(gameId, owner);
        GameStage stage = stages.findByIdAndGameId(stageId, gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return apply(stage, draft);
    }

    @Transactional
    public GameStage updateStage(Long gameId, Long stageId, UserAccount owner, StageDraft draft,
                                 StageEntryMode entryMode, String nextStageKey) {
        GameStage stage = updateStage(gameId, stageId, owner, draft);
        return applyStageFlow(stage, gameId, entryMode, nextStageKey);
    }

    @Transactional
    public GameStage configureStageFlow(Long gameId, Long stageId, UserAccount owner,
                                        StageEntryMode entryMode, String nextStageKey) {
        ownedGame(gameId, owner);
        GameStage stage = stages.findByIdAndGameId(stageId, gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return applyStageFlow(stage, gameId, entryMode, nextStageKey);
    }

    private GameStage applyStageFlow(GameStage stage, Long gameId, StageEntryMode entryMode, String nextStageKey) {
        String safeNextKey = nextStageKey == null ? stage.getNextStageKey() : optionalOrNull(nextStageKey, 36);
        if (safeNextKey != null) {
            GameStage next = stages.findAllByGameIdOrderByPositionAsc(gameId).stream()
                    .filter(candidate -> candidate.getStableKey().equals(safeNextKey))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("연결할 다음 문제가 존재하지 않습니다."));
            if (Objects.equals(next.getId(), stage.getId())) {
                throw new IllegalArgumentException("같은 문제로 다시 연결할 수 없습니다.");
            }
        }
        stage.setEntryMode(entryMode == null ? stage.getEntryMode() : entryMode);
        stage.setNextStageKey(safeNextKey);
        return stage;
    }

    @Transactional(readOnly = true)
    public GameStage ownedStage(Long stageId, UserAccount owner) {
        return stages.findByIdAndGameOwnerId(stageId, owner.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public GameStage ownedStage(Long gameId, Long stageId, UserAccount owner) {
        ownedGame(gameId, owner);
        return stages.findByIdAndGameId(stageId, gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional
    public Long deleteStage(Long stageId, UserAccount owner) {
        GameStage stage = stages.findByIdAndGameOwnerId(stageId, owner.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Long gameId = stage.getGame().getId();
        stages.delete(stage);
        stages.flush();
        normalizePositions(gameId);
        return gameId;
    }

    @Transactional
    public Long deleteStage(Long gameId, Long stageId, UserAccount owner) {
        ownedGame(gameId, owner);
        GameStage stage = stages.findByIdAndGameId(stageId, gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        stages.delete(stage);
        stages.flush();
        normalizePositions(gameId);
        return gameId;
    }

    @Transactional
    public Long moveStage(Long stageId, UserAccount owner, String direction) {
        GameStage stage = stages.findByIdAndGameOwnerId(stageId, owner.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<GameStage> ordered = stages.findAllByGameIdOrderByPositionAsc(stage.getGame().getId());
        int index = ordered.indexOf(stage);
        int target = "up".equalsIgnoreCase(direction) ? index - 1 :
                "down".equalsIgnoreCase(direction) ? index + 1 : index;
        if (index >= 0 && target >= 0 && target < ordered.size() && target != index) {
            Collections.swap(ordered, index, target);
            for (int i = 0; i < ordered.size(); i++) ordered.get(i).setPosition(i);
        }
        return stage.getGame().getId();
    }

    @Transactional
    public Long moveStage(Long gameId, Long stageId, UserAccount owner, String direction) {
        ownedGame(gameId, owner);
        GameStage stage = stages.findByIdAndGameId(stageId, gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<GameStage> ordered = stages.findAllByGameIdOrderByPositionAsc(gameId);
        int index = ordered.indexOf(stage);
        int target = "up".equalsIgnoreCase(direction) ? index - 1 :
                "down".equalsIgnoreCase(direction) ? index + 1 : index;
        if (index >= 0 && target >= 0 && target < ordered.size() && target != index) {
            Collections.swap(ordered, index, target);
            for (int i = 0; i < ordered.size(); i++) ordered.get(i).setPosition(i);
        }
        return gameId;
    }

    @Transactional
    public GameItem addItem(Long gameId, UserAccount owner, String name, String description, String emoji) {
        return addItem(gameId, owner, ItemType.CUSTOM, name, description, "", emoji, false, null);
    }

    @Transactional
    public GameItem addItem(Long gameId, UserAccount owner, ItemType itemType, String name,
                            String description, String clueText, String emoji,
                            boolean qrEnabled, String imageUrl) {
        return addItem(gameId, owner, itemType, name, description, clueText, emoji,
                qrEnabled, false, null, imageUrl);
    }

    @Transactional
    public GameItem addItem(Long gameId, UserAccount owner, ItemType itemType, String name,
                            String description, String clueText, String emoji,
                            boolean qrEnabled, boolean initiallyOwned, String copyableText,
                            String imageUrl) {
        return addItem(gameId, owner, itemType, name, description, clueText, emoji,
                qrEnabled, initiallyOwned, copyableText, imageUrl, null, null);
    }

    @Transactional
    public GameItem addItem(Long gameId, UserAccount owner, ItemType itemType, String name,
                            String description, String clueText, String emoji,
                            boolean qrEnabled, boolean initiallyOwned, String copyableText,
                            String imageUrl, String alternateRequiredItem, String alternateScanText) {
        EscapeGame game = ownedGame(gameId, owner);
        GameItem item = new GameItem(game, required(name, "아이템 이름", 80));
        item.setDescription(optional(description, 500));
        item.setEmoji(validatedIcon(emoji == null || emoji.isBlank() ? "🗝️" : emoji));
        item.setItemType(itemType == null ? ItemType.CUSTOM : itemType);
        item.setClueText(optional(clueText, 2_000));
        item.setQrEnabled(qrEnabled);
        item.setInitiallyOwned(initiallyOwned);
        item.setCopyableText(optionalOrNull(copyableText, 1_000));
        item.setImageUrl(validatedUploadUrl(imageUrl));
        item.setAlternateRequiredItem(validatedItemKey(gameId, alternateRequiredItem));
        item.setAlternateScanText(optionalOrNull(alternateScanText, 4_000));
        return items.save(item);
    }

    @Transactional
    public GameItem updateItem(Long gameId, Long itemId, UserAccount owner,
                               String name, String description, String emoji) {
        GameItem existing = ownedItem(gameId, itemId, owner);
        return updateItem(gameId, itemId, owner, existing.getItemType(), name, description,
                existing.getClueText(), emoji, existing.isQrEnabled(), existing.isInitiallyOwned(),
                existing.getCopyableText(), null);
    }

    @Transactional
    public GameItem updateItem(Long gameId, Long itemId, UserAccount owner, ItemType itemType,
                               String name, String description, String clueText, String emoji,
                               boolean qrEnabled, String newImageUrl) {
        GameItem existing = ownedItem(gameId, itemId, owner);
        return updateItem(gameId, itemId, owner, itemType, name, description, clueText, emoji,
                qrEnabled, existing.isInitiallyOwned(), existing.getCopyableText(), newImageUrl);
    }

    @Transactional
    public GameItem updateItem(Long gameId, Long itemId, UserAccount owner, ItemType itemType,
                               String name, String description, String clueText, String emoji,
                               boolean qrEnabled, boolean initiallyOwned, String copyableText,
                               String newImageUrl) {
        GameItem existing = ownedItem(gameId, itemId, owner);
        return updateItem(gameId, itemId, owner, itemType, name, description, clueText, emoji,
                qrEnabled, initiallyOwned, copyableText, newImageUrl,
                existing.getAlternateRequiredItem(), existing.getAlternateScanText());
    }

    @Transactional
    public GameItem updateItem(Long gameId, Long itemId, UserAccount owner, ItemType itemType,
                               String name, String description, String clueText, String emoji,
                               boolean qrEnabled, boolean initiallyOwned, String copyableText,
                               String newImageUrl, String alternateRequiredItem, String alternateScanText) {
        ownedGame(gameId, owner);
        GameItem item = items.findByIdAndGameId(itemId, gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        item.setName(required(name, "아이템 이름", 80));
        item.setDescription(optional(description, 500));
        item.setEmoji(validatedIcon(emoji == null || emoji.isBlank() ? "🗝️" : emoji));
        item.setItemType(itemType == null ? ItemType.CUSTOM : itemType);
        item.setClueText(optional(clueText, 2_000));
        item.setQrEnabled(qrEnabled);
        item.setInitiallyOwned(initiallyOwned);
        item.setCopyableText(optionalOrNull(copyableText, 1_000));
        String conditionKey = validatedItemKey(gameId, alternateRequiredItem);
        if (item.getStableKey().equals(conditionKey)) {
            throw new IllegalArgumentException("아이템 자신을 대체 QR 장면 조건으로 사용할 수 없습니다.");
        }
        item.setAlternateRequiredItem(conditionKey);
        item.setAlternateScanText(optionalOrNull(alternateScanText, 4_000));
        if (newImageUrl != null && !newImageUrl.isBlank()) item.setImageUrl(validatedUploadUrl(newImageUrl));
        return item;
    }

    @Transactional(readOnly = true)
    public GameItem ownedItem(Long gameId, Long itemId, UserAccount owner) {
        ownedGame(gameId, owner);
        return items.findByIdAndGameId(itemId, gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional
    public Long deleteItem(Long itemId, UserAccount owner) {
        GameItem item = items.findByIdAndGameOwnerId(itemId, owner.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Long gameId = item.getGame().getId();
        for (GameStage stage : stages.findAllByGameIdOrderByPositionAsc(gameId)) {
            if (stage.getRequiredItems().contains(item.getStableKey())) {
                stage.setRequiredItems(stage.getRequiredItems().stream()
                        .filter(key -> !item.getStableKey().equals(key)).toList());
            }
            if (item.getStableKey().equals(stage.getRewardItem())) stage.setRewardItem(null);
        }
        for (GameItem candidate : items.findAllByGameIdOrderByIdAsc(gameId)) {
            if (item.getStableKey().equals(candidate.getAlternateRequiredItem())) {
                candidate.setAlternateRequiredItem(null);
                candidate.setAlternateScanText(null);
            }
        }
        items.delete(item);
        return gameId;
    }

    @Transactional
    public Long deleteItem(Long gameId, Long itemId, UserAccount owner) {
        ownedGame(gameId, owner);
        GameItem item = items.findByIdAndGameId(itemId, gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        for (GameStage stage : stages.findAllByGameIdOrderByPositionAsc(gameId)) {
            if (stage.getRequiredItems().contains(item.getStableKey())) {
                stage.setRequiredItems(stage.getRequiredItems().stream()
                        .filter(key -> !item.getStableKey().equals(key)).toList());
            }
            if (item.getStableKey().equals(stage.getRewardItem())) stage.setRewardItem(null);
        }
        for (GameItem candidate : items.findAllByGameIdOrderByIdAsc(gameId)) {
            if (item.getStableKey().equals(candidate.getAlternateRequiredItem())) {
                candidate.setAlternateRequiredItem(null);
                candidate.setAlternateScanText(null);
            }
        }
        items.delete(item);
        return gameId;
    }

    @Transactional
    public EscapeGame hide(Long gameId, UserAccount owner) {
        EscapeGame game = ownedGame(gameId, owner);
        game.setStatus(GameStatus.HIDDEN);
        return game;
    }

    @Transactional
    public EscapeGame platformToggle(Long gameId) {
        EscapeGame game = games.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (game.getStatus() == GameStatus.HIDDEN) {
            game.setStatus(game.getPublishedVersion() > 0 ? GameStatus.PUBLISHED : GameStatus.DRAFT);
        } else {
            game.setStatus(GameStatus.HIDDEN);
        }
        return game;
    }

    @Transactional(readOnly = true)
    public List<EscapeGame> publicGames() {
        return enrich(games.findAllByVisibilityAndStatusOrderByUpdatedAtDesc(GameVisibility.PUBLIC, GameStatus.PUBLISHED));
    }

    @Transactional(readOnly = true)
    public List<EscapeGame> allGames() { return enrich(games.findAllByOrderByUpdatedAtDesc()); }

    @Transactional(readOnly = true)
    public MakerStats makerStats(UserAccount owner) {
        long totalGames = games.countByOwnerId(owner.getId());
        long publishedGames = games.countByOwnerIdAndStatus(owner.getId(), GameStatus.PUBLISHED);
        long totalPlays = ownedGames(owner).stream().mapToLong(g -> plays.countByRelease_Game_Id(g.getId())).sum();
        long completions = ownedGames(owner).stream().mapToLong(g ->
                plays.countByRelease_Game_IdAndStatus(g.getId(), PlayStatus.COMPLETED)).sum();
        return new MakerStats(totalGames, publishedGames, totalPlays, completions);
    }

    @Transactional(readOnly = true)
    public GameStats gameStats(Long gameId, UserAccount owner) {
        ownedGame(gameId, owner);
        long total = plays.countByRelease_Game_Id(gameId);
        long completed = plays.countByRelease_Game_IdAndStatus(gameId, PlayStatus.COMPLETED);
        return new GameStats(total, completed, total == 0 ? 0 : Math.round(completed * 1000.0 / total) / 10.0);
    }

    private GameStage apply(GameStage stage, StageDraft draft) {
        stage.setTitle(required(draft.title(), "스테이지 제목", 120));
        stage.setStory(optional(draft.story(), 20_000));
        stage.setInstruction(optional(draft.instruction(), 500));
        stage.setHint(optional(draft.hint(), 500));
        stage.setPuzzleType(draft.puzzleType() == null ? PuzzleType.STORY : draft.puzzleType());
        stage.setDraftAnswer(optionalOrNull(draft.draftAnswer(), 500));
        stage.setOptionsText(optionalOrNull(draft.optionsText(), 2_000));
        stage.setLockLength(clamp(draft.lockLength(), 1, 12));
        List<String> requiredItems = draft.requiredItems() == null ? List.of() : draft.requiredItems().stream()
                .map(value -> optionalOrNull(value, 36))
                .filter(Objects::nonNull)
                .distinct()
                .limit(30)
                .toList();
        if (requiredItems.isEmpty()) {
            String requiredItem = optionalOrNull(draft.requiredItem(), 36);
            stage.setRequiredItems(requiredItem == null ? List.of() : List.of(requiredItem));
        } else {
            stage.setRequiredItems(requiredItems);
        }
        stage.setConsumeRequiredItems(draft.consumeRequiredItems());
        stage.setRewardItem(optionalOrNull(draft.rewardItem(), 36));
        stage.setQrEnabled(draft.qrEnabled());
        if (draft.storyEffect() != null) stage.setStoryEffect(draft.storyEffect());
        if (draft.sceneImageUrl() != null) {
            stage.setSceneImageUrl(validatedSceneImageUrl(draft.sceneImageUrl()));
        }
        if (draft.sfxUrl() != null) stage.setSfxUrl(validatedAudioUrl(draft.sfxUrl(), "효과음"));
        if (draft.sfxTitle() != null) stage.setSfxTitle(optionalOrNull(draft.sfxTitle(), 200));
        if (draft.sfxCreator() != null) stage.setSfxCreator(optionalOrNull(draft.sfxCreator(), 200));
        if (draft.sfxLicense() != null) stage.setSfxLicense(optionalOrNull(draft.sfxLicense(), 100));
        if (draft.sfxLicenseUrl() != null) {
            stage.setSfxLicenseUrl(validatedHttpsUrl(draft.sfxLicenseUrl(), "효과음 라이선스 URL"));
        }
        if (draft.sfxSourceUrl() != null) {
            stage.setSfxSourceUrl(validatedHttpsUrl(draft.sfxSourceUrl(), "효과음 출처 URL"));
        }
        if (draft.sfxVolume() != null) stage.setSfxVolume(validatedVolume(draft.sfxVolume(), "효과음 볼륨"));
        return stage;
    }

    private void applyTemplate(EscapeGame game, String template) {
        String kind = Objects.toString(template, "MYSTERY_MANSION").toUpperCase(Locale.ROOT);
        kind = switch (kind) {
            case "MYSTERY" -> "MYSTERY_MANSION";
            case "QUICK" -> "QUICK_10";
            case "TREASURE" -> "TREASURE_HUNT";
            case "HORROR" -> "HORROR_HOSPITAL";
            case "STORY" -> "BLANK";
            default -> kind;
        };
        addTemplateStage(game, "프롤로그", PuzzleType.STORY, null, "낯선 공간에서 눈을 떴습니다. 주변을 살펴 탈출의 실마리를 찾으세요.");
        switch (kind) {
            case "BLANK" -> { return; }
            case "QUICK_10" -> addTemplateStage(game, "10분 숫자 금고", PuzzleType.NUMBER_LOCK, "1234", "포스터 모서리의 네 숫자를 차례로 맞춰 보세요.");
            case "TREASURE_HUNT" -> {
                addTemplateItem(game, ItemType.MAP, "해적의 보물 지도", "섬의 방향 표식이 그려진 낡은 지도", "🗺️", "붉은 X 옆의 방향을 순서대로 읽으세요.", true);
                addTemplateStage(game, "해적의 나침반", PuzzleType.DIRECTION_LOCK, "UP,RIGHT,DOWN,LEFT", "낡은 지도 가장자리의 방향을 따라가세요.");
                addTemplateStage(game, "보석 상자", PuzzleType.COLOR_LOCK, "RED,GREEN,BLUE,YELLOW", "보석이 빛나는 색의 순서를 기억하세요.");
            }
            case "HORROR_HOSPITAL" -> {
                addTemplateStage(game, "금지된 이름", PuzzleType.ALPHABET_LOCK, "GHOST", "거울에 떠오른 알파벳을 맞추세요.");
                addTemplateStage(game, "지하실 키패드", PuzzleType.KEYPAD, "0606", "찢긴 달력에 남은 날짜가 암호입니다.");
            }
            case "DETECTIVE_CASE" -> {
                addTemplateItem(game, ItemType.EVIDENCE, "현장 증거 봉투", "사건 현장에서 회수한 작은 증거", "🔎", "봉투 안 메모에는 '멈춘 시간'이라고 적혀 있습니다.", true);
                addTemplateStage(game, "용의자 진술", PuzzleType.MULTIPLE_CHOICE, "관리인", "진술의 모순을 찾아 범인을 지목하세요.", "경비원\n관리인\n방문객");
                addTemplateStage(game, "사라진 증거", PuzzleType.TEXT_ANSWER, "시계", "사건 현장에서 멈춰 있던 물건의 이름을 입력하세요.");
            }
            case "SCHOOL_MISSION" -> {
                addTemplateItem(game, ItemType.DOCUMENT, "비밀 시간표", "형광펜으로 숫자가 표시된 시간표", "📋", "강조된 교시 번호를 왼쪽부터 읽으세요.", true);
                addTemplateStage(game, "사물함 번호", PuzzleType.NUMBER_LOCK, "2026", "시간표 속 강조된 숫자를 조합하세요.");
                addTemplateStage(game, "동아리 퀴즈", PuzzleType.MULTIPLE_CHOICE, "과학실", "마지막 단서가 숨겨진 교실을 고르세요.", "도서관\n과학실\n음악실");
            }
            case "MUSEUM_TOUR" -> {
                addTemplateItem(game, ItemType.PHOTO, "전시 작품 엽서", "뒷면에 큐레이터 메모가 적힌 엽서", "🖼️", "서명의 첫 두 글자가 다음 단서입니다.", true);
                addTemplateStage(game, "작품의 서명", PuzzleType.TEXT_ANSWER, "모나", "작품 설명에서 반복되는 이름을 찾으세요.");
                addTemplateStage(game, "빛의 전시실", PuzzleType.COLOR_LOCK, "BLUE,YELLOW,RED", "조명이 켜지는 색 순서를 기억하세요.");
            }
            case "SCI_FI_LAB" -> {
                addTemplateStage(game, "격리실 키패드", PuzzleType.KEYPAD, "2049", "실험 로그의 마지막 네 자리입니다.");
                addTemplateStage(game, "AI 호출 부호", PuzzleType.ALPHABET_LOCK, "NOVA", "홀로그램에 표시된 호출 부호를 맞추세요.");
            }
            case "FANTASY_QUEST" -> {
                addTemplateItem(game, ItemType.SYMBOL, "정령의 룬", "희미하게 빛나는 고대 룬", "🔮", "룬의 빛은 초록, 파랑, 보라 순서로 깨어납니다.", true);
                addTemplateStage(game, "정령의 빛", PuzzleType.COLOR_LOCK, "GREEN,BLUE,PURPLE", "정령석이 깨어나는 색의 순서를 따르세요.");
                addTemplateStage(game, "고대 룬의 길", PuzzleType.DIRECTION_LOCK, "LEFT,UP,RIGHT,DOWN", "석판의 룬이 가리키는 길을 입력하세요.");
            }
            case "OUTDOOR_TRAIL" -> {
                addTemplateItem(game, ItemType.MAP, "탐방 지도", "표식과 거리가 표시된 야외 지도", "🧭", "삼각형 표식의 방향만 이어서 읽으세요.", true);
                addTemplateStage(game, "숲길 표식", PuzzleType.DIRECTION_LOCK, "UP,LEFT,RIGHT,UP", "나무 표식이 가리키는 방향을 따라가세요.");
                addTemplateStage(game, "탐방로 표지", PuzzleType.NUMBER_LOCK, "1372", "네 이정표의 거리를 순서대로 입력하세요.");
            }
            case "FESTIVAL_EVENT" -> {
                addTemplateItem(game, ItemType.DOCUMENT, "축제 스탬프 카드", "부스별 색과 번호가 찍힌 카드", "🎟️", "스탬프를 받은 시간 순서가 색상 순서입니다.", true);
                addTemplateStage(game, "불꽃의 순서", PuzzleType.COLOR_LOCK, "RED,YELLOW,BLUE,PURPLE", "불꽃이 터진 색 순서를 기억하세요.");
                addTemplateStage(game, "행운권 번호", PuzzleType.NUMBER_LOCK, "7777", "네 부스에서 얻은 행운 숫자를 모으세요.");
            }
            case "KIDS_ADVENTURE" -> {
                addTemplateStage(game, "동물 친구 찾기", PuzzleType.MULTIPLE_CHOICE, "토끼", "당근 발자국의 주인을 골라 보세요.", "토끼\n여우\n곰");
                addTemplateStage(game, "별빛 상자", PuzzleType.NUMBER_LOCK, "2468", "짝수 별의 개수를 차례로 세어 보세요.");
            }
            case "TEAM_RACE" -> {
                addTemplateStage(game, "첫 번째 팀 코드", PuzzleType.KEYPAD, "1357", "팀원 네 명의 카드에서 숫자를 모으세요.");
                addTemplateStage(game, "릴레이 방향", PuzzleType.DIRECTION_LOCK, "RIGHT,UP,LEFT,DOWN", "각 주자가 받은 방향을 이어 붙이세요.");
                addTemplateStage(game, "결승 구호", PuzzleType.TEXT_ANSWER, "함께", "모든 단서의 첫 글자로 결승 구호를 만드세요.");
            }
            case "MYSTERY_MANSION" -> {
                addTemplateItem(game, ItemType.DOCUMENT, "관리인의 쪽지", "저택 관리인이 남긴 접힌 쪽지", "📜", "원주율의 앞 네 자리와 나침반 자국을 기억하세요.", true);
                addTemplateStage(game, "서재 금고", PuzzleType.KEYPAD, "3141", "관리인의 쪽지와 책상 위 원형 문양을 살펴보세요.");
                addTemplateStage(game, "저택의 복도", PuzzleType.DIRECTION_LOCK, "UP,RIGHT,DOWN,LEFT", "액자 뒤 나침반 자국을 순서대로 입력하세요.");
            }
            default -> {
                addTemplateStage(game, "금고의 암호", PuzzleType.KEYPAD, "3141", "책상 위 단서로 네 자리 암호를 찾으세요.");
                addTemplateStage(game, "마지막 방향", PuzzleType.DIRECTION_LOCK, "UP,RIGHT,DOWN,LEFT", "나침반 자국이 가리키는 순서를 입력하세요.");
            }
        }
        addTemplateStage(game, "탈출", PuzzleType.STORY, null, "문이 열렸습니다. 축하합니다!");
    }

    private GameStage addTemplateStage(EscapeGame game, String title, PuzzleType type, String answer, String story) {
        return addTemplateStage(game, title, type, answer, story, null);
    }

    private GameStage addTemplateStage(EscapeGame game, String title, PuzzleType type, String answer, String story, String optionsText) {
        int position = stages.findAllByGameIdOrderByPositionAsc(game.getId()).size();
        GameStage stage = new GameStage(game, position, title);
        stage.setPuzzleType(type);
        stage.setDraftAnswer(answer);
        stage.setStory(story);
        stage.setOptionsText(optionsText);
        stage.setInstruction(type == PuzzleType.STORY ? "계속 버튼을 눌러 진행하세요." : "정답을 입력해 자물쇠를 해제하세요.");
        return stages.save(stage);
    }

    private GameItem addTemplateItem(EscapeGame game, ItemType type, String name, String description,
                                     String emoji, String clueText, boolean qrEnabled) {
        GameItem item = new GameItem(game, name);
        item.setItemType(type);
        item.setDescription(description);
        item.setEmoji(emoji);
        item.setClueText(clueText);
        item.setQrEnabled(qrEnabled);
        return items.save(item);
    }

    private void normalizePositions(Long gameId) {
        List<GameStage> ordered = stages.findAllByGameIdOrderByPositionAsc(gameId);
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).setPosition(i);
    }

    private List<EscapeGame> enrich(List<EscapeGame> values) {
        values.forEach(game -> {
            long total = plays.countByRelease_Game_Id(game.getId());
            long completed = plays.countByRelease_Game_IdAndStatus(game.getId(), PlayStatus.COMPLETED);
            game.setStageCount(stages.countByGameId(game.getId()));
            game.setPlayCount(total);
            game.setCompletionRate(total == 0 ? 0 : Math.round(completed * 1000.0 / total) / 10.0);
        });
        return values;
    }

    private String uniqueSlug(String requested, String title, Long currentId) {
        String base = slugify(requested);
        if (base.isBlank()) base = slugify(title);
        if (base.isBlank()) base = "escape-" + UUID.randomUUID().toString().substring(0, 8);
        String candidate = base;
        int suffix = 2;
        while (games.findBySlug(candidate).filter(game -> !Objects.equals(game.getId(), currentId)).isPresent()) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private String slugify(String value) {
        String normalized = Normalizer.normalize(Objects.toString(value, ""), Normalizer.Form.NFKD)
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.length() > 70 ? normalized.substring(0, 70).replaceAll("-$", "") : normalized;
    }

    private String required(String value, String label, int max) {
        String cleaned = Objects.toString(value, "").trim();
        if (cleaned.isBlank() || cleaned.length() > max) throw new IllegalArgumentException(label + " 입력을 확인해 주세요.");
        return cleaned;
    }
    private String optional(String value, int max) {
        String cleaned = Objects.toString(value, "").trim();
        if (cleaned.length() > max) throw new IllegalArgumentException("입력 가능한 글자 수를 초과했습니다.");
        return cleaned;
    }
    private String optionalOrNull(String value, int max) {
        String cleaned = optional(value, max);
        return cleaned.isBlank() ? null : cleaned;
    }
    private String validatedItemKey(Long gameId, String value) {
        String key = optionalOrNull(value, 36);
        if (key == null) return null;
        boolean exists = items.findAllByGameIdOrderByIdAsc(gameId).stream()
                .anyMatch(item -> item.getStableKey().equals(key));
        if (!exists) throw new IllegalArgumentException("대체 QR 장면 조건 아이템이 존재하지 않습니다.");
        return key;
    }
    private String validatedImageUrl(String value) {
        String cleaned = optional(value, 1000);
        if (cleaned.isBlank()) return null;
        if (isBundledImagePath(cleaned)) return cleaned;
        try {
            java.net.URI uri = java.net.URI.create(cleaned);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) {
                throw new IllegalArgumentException("대표 이미지 URL은 http 또는 https 주소여야 합니다.");
            }
            return uri.toString();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("대표 이미지 URL은 http 또는 https 주소여야 합니다.");
        }
    }
    private String validatedAccent(String value) {
        return validatedColor(value, "강조 색상", "#8B5CF6");
    }
    private String validatedColor(String value, String label, String defaultValue) {
        String cleaned = Objects.toString(value, defaultValue).trim();
        if (cleaned.isBlank()) cleaned = defaultValue;
        if (!cleaned.matches("#[0-9A-Fa-f]{6}")) throw new IllegalArgumentException(label + "은 #RRGGBB 형식이어야 합니다.");
        return cleaned.toUpperCase(Locale.ROOT);
    }
    private String validatedIcon(String value) {
        String cleaned = Objects.toString(value, "").trim();
        if (cleaned.isBlank()) return "🔐";
        if (cleaned.length() > 16 || cleaned.chars().anyMatch(Character::isISOControl)
                || cleaned.matches(".*[<>&\\\"'].*")) {
            throw new IllegalArgumentException("아이콘은 안전한 16자 이하 문자 또는 이모지여야 합니다.");
        }
        return cleaned;
    }
    private String validatedUploadUrl(String value) {
        if (value == null || value.isBlank()) return null;
        if (isBundledImagePath(value)) return value;
        if (!value.matches("/uploads/[0-9a-fA-F-]{36}\\.(jpg|png)")) {
            throw new IllegalArgumentException("허용되지 않은 이미지 경로입니다.");
        }
        return value;
    }
    private String validatedSceneImageUrl(String value) {
        String cleaned = optional(value, 1000);
        if (cleaned.isBlank()) return null;
        if (isBundledImagePath(cleaned)) return cleaned;
        if (cleaned.matches("/uploads/" + UUID_PATH + "\\.(jpg|png)")) return cleaned;
        return validatedHttpsUrl(cleaned, "장면 이미지 URL");
    }

    private boolean isBundledImagePath(String value) {
        String cleaned = Objects.toString(value, "").trim();
        return !cleaned.contains("..") && !cleaned.contains("\\")
                && !cleaned.contains("?") && !cleaned.contains("#")
                && cleaned.chars().noneMatch(Character::isISOControl)
                && cleaned.matches("/images/[\\p{L}\\p{N} _./-]+\\.(?i:jpg|jpeg|png|webp|gif)");
    }
    private String validatedAudioUrl(String value, String label) {
        String cleaned = optional(value, 1000);
        if (cleaned.isBlank()) return null;
        if (cleaned.matches("/uploads/audio/" + UUID_PATH + "\\.(mp3|ogg|wav)")) return cleaned;
        return validatedHttpsUrl(cleaned, label + " URL");
    }
    private String validatedHttpsUrl(String value, String label) {
        String cleaned = optional(value, 1000);
        if (cleaned.isBlank()) return null;
        try {
            java.net.URI uri = java.net.URI.create(cleaned);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException(label + "은 HTTPS 주소여야 합니다.");
            }
            return uri.toASCIIString();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(label + "은 HTTPS 주소여야 합니다.");
        }
    }
    private double validatedVolume(double value, String label) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(label + "은 0부터 1 사이여야 합니다.");
        }
        return value;
    }
    private int validatedRange(int value, int min, int max, String label) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(label + "는 " + min + "부터 " + max + " 사이여야 합니다.");
        }
        return value;
    }
    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value <= 0 ? min : value)); }

    public record StageDraft(String title, String story, String instruction, String hint,
                             PuzzleType puzzleType, String draftAnswer, String optionsText,
                             int lockLength, String requiredItem, String rewardItem,
                             boolean qrEnabled,
                             StoryEffect storyEffect, String sceneImageUrl,
                             String sfxUrl, String sfxTitle, String sfxCreator, String sfxLicense,
                             String sfxLicenseUrl, String sfxSourceUrl, Double sfxVolume,
                             List<String> requiredItems, boolean consumeRequiredItems) {
        public StageDraft(String title, String story, String instruction, String hint,
                          PuzzleType puzzleType, String draftAnswer, String optionsText,
                          int lockLength, String requiredItem, String rewardItem) {
            this(title, story, instruction, hint, puzzleType, draftAnswer, optionsText,
                    lockLength, requiredItem, rewardItem, true, null, null,
                    null, null, null, null, null, null, null, null, false);
        }

        public StageDraft(String title, String story, String instruction, String hint,
                          PuzzleType puzzleType, String draftAnswer, String optionsText,
                          int lockLength, String requiredItem, String rewardItem,
                          StoryEffect storyEffect, String sceneImageUrl,
                          String sfxUrl, String sfxTitle, String sfxCreator, String sfxLicense,
                          String sfxLicenseUrl, String sfxSourceUrl, Double sfxVolume) {
            this(title, story, instruction, hint, puzzleType, draftAnswer, optionsText,
                    lockLength, requiredItem, rewardItem, true, storyEffect, sceneImageUrl,
                    sfxUrl, sfxTitle, sfxCreator, sfxLicense, sfxLicenseUrl, sfxSourceUrl, sfxVolume,
                    null, false);
        }

        public StageDraft(String title, String story, String instruction, String hint,
                          PuzzleType puzzleType, String draftAnswer, String optionsText,
                          int lockLength, String requiredItem, String rewardItem,
                          boolean qrEnabled,
                          StoryEffect storyEffect, String sceneImageUrl,
                          String sfxUrl, String sfxTitle, String sfxCreator, String sfxLicense,
                          String sfxLicenseUrl, String sfxSourceUrl, Double sfxVolume) {
            this(title, story, instruction, hint, puzzleType, draftAnswer, optionsText,
                    lockLength, requiredItem, rewardItem, qrEnabled, storyEffect, sceneImageUrl,
                    sfxUrl, sfxTitle, sfxCreator, sfxLicense, sfxLicenseUrl, sfxSourceUrl, sfxVolume,
                    null, false);
        }

        public StageDraft(String title, String story, String instruction, String hint,
                          PuzzleType puzzleType, String draftAnswer, String optionsText,
                          int lockLength, List<String> requiredItems, String rewardItem,
                          boolean consumeRequiredItems) {
            this(title, story, instruction, hint, puzzleType, draftAnswer, optionsText,
                    lockLength, null, rewardItem, true, null, null,
                    null, null, null, null, null, null, null,
                    requiredItems, consumeRequiredItems);
        }
    }

    public record MakerStats(long totalGames, long publishedGames, long totalPlays, long completedPlays) {
        public long getTotalGames() { return totalGames; }
        public long getPublishedGames() { return publishedGames; }
        public long getTotalPlays() { return totalPlays; }
        public long getCompletedPlays() { return completedPlays; }
        public long getGameCount() { return totalGames; }
        public long getPublishedCount() { return publishedGames; }
        public double getCompletionRate() {
            return totalPlays == 0 ? 0 : Math.round(completedPlays * 1000.0 / totalPlays) / 10.0;
        }
    }

    public record GameStats(long totalPlays, long completedPlays, double completionRate) {
        public long getTotalPlays() { return totalPlays; }
        public long getCompletedPlays() { return completedPlays; }
        public double getCompletionRate() { return completionRate; }
    }
}
