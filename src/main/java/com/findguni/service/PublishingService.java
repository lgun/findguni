package com.findguni.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.findguni.model.*;
import com.findguni.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class PublishingService {
    private final EscapeGameRepository games;
    private final GameStageRepository stages;
    private final GameItemRepository items;
    private final GameReleaseRepository releases;
    private final AnswerCodec answers;
    private final ObjectMapper objectMapper;

    public PublishingService(EscapeGameRepository games, GameStageRepository stages,
                             GameItemRepository items, GameReleaseRepository releases,
                             AnswerCodec answers, ObjectMapper objectMapper) {
        this.games = games;
        this.stages = stages;
        this.items = items;
        this.releases = releases;
        this.answers = answers;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public GameRelease publish(Long gameId, UserAccount owner) {
        EscapeGame game = games.findByIdAndOwnerId(gameId, owner.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<GameStage> draftStages = stages.findAllByGameIdOrderByPositionAsc(gameId);
        List<GameItem> draftItems = items.findAllByGameIdOrderByPositionAscIdAsc(gameId);
        validate(game, draftStages, draftItems);

        ReleaseSnapshot snapshot = snapshot(game, draftStages, draftItems);

        int nextVersion = game.getPublishedVersion() + 1;
        GameRelease release = releases.save(new GameRelease(game, nextVersion, toJson(snapshot)));
        game.setPublishedVersion(nextVersion);
        game.setStatus(GameStatus.PUBLISHED);
        return release;
    }

    @Transactional(readOnly = true)
    public ReleaseSnapshot snapshot(EscapeGame game) {
        List<GameStage> draftStages = stages.findAllByGameIdOrderByPositionAsc(game.getId());
        List<GameItem> draftItems = items.findAllByGameIdOrderByPositionAscIdAsc(game.getId());
        return snapshot(game, draftStages, draftItems);
    }

    @Transactional(readOnly = true)
    public GameRelease currentRelease(EscapeGame game) {
        if (game.getPublishedVersion() < 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return releases.findByGameIdAndVersionNumber(game.getId(), game.getPublishedVersion())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private ReleaseSnapshot snapshot(EscapeGame game, List<GameStage> draftStages, List<GameItem> draftItems) {
        List<ReleaseSnapshot.StageSnapshot> stageSnapshots = draftStages.stream()
                .map(stage -> new ReleaseSnapshot.StageSnapshot(
                        stage.getStableKey(),
                        stage.getPosition(),
                        stage.getTitle(),
                        stage.getStory(),
                        stage.getInstruction(),
                        stage.getHint(),
                        stage.getPuzzleType(),
                        stage.getPuzzleType() != null && stage.getPuzzleType().requiresAnswer()
                                && stage.getDraftAnswer() != null && !stage.getDraftAnswer().isBlank()
                                ? answers.digest(stage.getPuzzleType(), stage.getDraftAnswer())
                                : null,
                        parseOptions(stage.getOptionsText()),
                        parseOptionRoutes(stage.getOptionRoutesJson()),
                        stage.getLockLength(),
                        stage.getRequiredItem(),
                        stage.getRequiredItems(),
                        stage.isConsumeRequiredItems(),
                        stage.getRewardItem(),
                        stage.isQrEnabled(),
                        stage.getEntryMode(),
                        stage.getNextStageKey(),
                        stage.getStoryEffect(),
                        stage.getSceneImageUrl(),
                        stage.getSfxUrl(),
                        stage.getSfxTitle(),
                        stage.getSfxCreator(),
                        stage.getSfxLicense(),
                        stage.getSfxLicenseUrl(),
                        stage.getSfxSourceUrl(),
                        stage.getSfxVolume()
                )).toList();

        List<ReleaseSnapshot.ItemSnapshot> itemSnapshots = draftItems.stream()
                .map(item -> new ReleaseSnapshot.ItemSnapshot(
                        item.getStableKey(),
                        item.getName(),
                        item.getDescription(),
                        item.getEmoji(),
                        item.getItemType(),
                        item.getImageUrl(),
                        item.getClueText(),
                        item.isQrEnabled(),
                        item.isInitiallyOwned(),
                        item.getCopyableText(),
                        item.getAlternateRequiredItem(),
                        item.getAlternateScanText()
                )).toList();

        return new ReleaseSnapshot(
                game.getId(),
                game.getSlug(),
                game.getTitle(),
                game.getSummary(),
                game.getIntro(),
                game.getCoverImageUrl(),
                game.getAccentColor(),
                game.getSecondaryColor(),
                game.getBackgroundColor(),
                game.getGameIcon(),
                game.isAllowNotebook(),
                game.isAllowCluebook(),
                game.isAllowQrScanner(),
                game.getFlowMode(),
                game.getBgmUrl(),
                game.getBgmTitle(),
                game.getBgmCreator(),
                game.getBgmLicense(),
                game.getBgmLicenseUrl(),
                game.getBgmSourceUrl(),
                game.getBgmVolume(),
                game.isBgmLoop(),
                game.getStoryTextSpeed(),
                game.isEnableVignette(),
                game.getTheme(),
                game.getDifficulty(),
                game.getEstimatedMinutes(),
                stageSnapshots,
                itemSnapshots,
                game.isUnlimitedHints(),
                game.getHintLimit(),
                game.getHintCooldownSeconds()
        );
    }

    public ReleaseSnapshot readSnapshot(GameRelease release) {
        try {
            return objectMapper.readValue(release.getSnapshotJson(), ReleaseSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("발행본 스냅샷을 읽을 수 없습니다.", e);
        }
    }

    private void validate(EscapeGame game, List<GameStage> draftStages, List<GameItem> draftItems) {
        if (draftStages.isEmpty()) throw new IllegalArgumentException("스테이지를 한 개 이상 만들어 주세요.");
        Set<String> itemKeys = new HashSet<>();
        Set<String> stageKeys = new HashSet<>();
        draftItems.forEach(item -> itemKeys.add(item.getStableKey()));
        draftStages.forEach(stage -> stageKeys.add(stage.getStableKey()));
        for (GameItem item : draftItems) {
            if (item.getAlternateRequiredItem() != null
                    && !itemKeys.contains(item.getAlternateRequiredItem())) {
                throw new IllegalArgumentException("'" + item.getName()
                        + "'의 대체 QR 장면 조건 아이템이 존재하지 않습니다.");
            }
            if (item.getAlternateRequiredItem() != null
                    && (item.getAlternateScanText() == null || item.getAlternateScanText().isBlank())) {
                throw new IllegalArgumentException("'" + item.getName()
                        + "'의 대체 QR 장면 내용을 입력해 주세요.");
            }
        }
        for (GameStage stage : draftStages) {
            List<String> stageOptions = parseOptions(stage.getOptionsText());
            Map<String, ReleaseSnapshot.OptionRoute> optionRoutes = parseOptionRoutes(stage.getOptionRoutesJson());
            boolean allOptionsRouted = stage.getPuzzleType() == PuzzleType.MULTIPLE_CHOICE
                    && !stageOptions.isEmpty() && optionRoutes.keySet().containsAll(stageOptions)
                    && optionRoutes.values().stream().allMatch(ReleaseSnapshot.OptionRoute::complete);
            if (stage.getPuzzleType().requiresAnswer() && !allOptionsRouted &&
                    (stage.getDraftAnswer() == null || answers.normalize(stage.getPuzzleType(), stage.getDraftAnswer()).isBlank())) {
                throw new IllegalArgumentException("'" + stage.getTitle() + "' 스테이지의 정답을 입력해 주세요.");
            }
            if (stage.getPuzzleType() == PuzzleType.MULTIPLE_CHOICE && stageOptions.size() < 2) {
                throw new IllegalArgumentException("'" + stage.getTitle() + "' 객관식 선택지를 두 개 이상 입력해 주세요.");
            }
            for (Map.Entry<String, ReleaseSnapshot.OptionRoute> route : optionRoutes.entrySet()) {
                if (!stageOptions.contains(route.getKey())) {
                    throw new IllegalArgumentException("'" + stage.getTitle() + "'의 존재하지 않는 선택지에 이동 경로가 연결되어 있습니다.");
                }
                if (!route.getValue().complete()) {
                    throw new IllegalArgumentException("'" + stage.getTitle() + "' 선택지의 아이템 보유·미보유 이동 페이지를 모두 지정해 주세요.");
                }
                if (route.getValue().conditional() && !itemKeys.contains(route.getValue().getRequiredItemKey())) {
                    throw new IllegalArgumentException("'" + stage.getTitle() + "' 선택지의 조건 아이템이 존재하지 않습니다.");
                }
                for (String target : route.getValue().targets()) {
                    if (!stageKeys.contains(target)) {
                        throw new IllegalArgumentException("'" + stage.getTitle() + "' 선택지의 이동 대상 문제가 존재하지 않습니다.");
                    }
                    if (Objects.equals(stage.getStableKey(), target)) {
                        throw new IllegalArgumentException("'" + stage.getTitle() + "' 선택지를 자기 자신으로 연결할 수 없습니다.");
                    }
                }
            }
            if (!itemKeys.containsAll(stage.getRequiredItems())) {
                throw new IllegalArgumentException("'" + stage.getTitle() + "'의 필요 아이템이 존재하지 않습니다.");
            }
            if (stage.getRewardItem() != null && !itemKeys.contains(stage.getRewardItem())) {
                throw new IllegalArgumentException("'" + stage.getTitle() + "'의 보상 아이템이 존재하지 않습니다.");
            }
            if (stage.getNextStageKey() != null && !stageKeys.contains(stage.getNextStageKey())) {
                throw new IllegalArgumentException("'" + stage.getTitle() + "'에서 연결할 다음 문제가 존재하지 않습니다.");
            }
            if (Objects.equals(stage.getStableKey(), stage.getNextStageKey())) {
                throw new IllegalArgumentException("'" + stage.getTitle() + "' 문제를 자기 자신으로 연결할 수 없습니다.");
            }
        }
        validateSolvableFlow(game.getFlowMode(), draftStages, draftItems);
    }

    private void validateSolvableFlow(GameFlowMode flowMode, List<GameStage> draftStages, List<GameItem> draftItems) {
        Set<String> availableItems = new HashSet<>();
        draftItems.stream().filter(item -> item.isQrEnabled() || item.isInitiallyOwned())
                .map(GameItem::getStableKey).forEach(availableItems::add);

        if (flowMode == null || flowMode == GameFlowMode.LINEAR) {
            for (GameStage stage : draftStages) {
                if (!hasRequirement(stage, availableItems)) {
                    throw unreachableStage(stage);
                }
                grantReward(stage, availableItems);
            }
            return;
        }

        Set<String> discoveredStages = new HashSet<>();
        draftStages.stream().filter(stage -> stage.getEntryMode() != StageEntryMode.LINKED)
                .map(GameStage::getStableKey).forEach(discoveredStages::add);
        List<GameStage> remaining = new ArrayList<>(draftStages);
        boolean progressed;
        do {
            progressed = false;
            Iterator<GameStage> iterator = remaining.iterator();
            while (iterator.hasNext()) {
                GameStage stage = iterator.next();
                if (!discoveredStages.contains(stage.getStableKey()) || !hasRequirement(stage, availableItems)) continue;
                grantReward(stage, availableItems);
                if (stage.getNextStageKey() != null) discoveredStages.add(stage.getNextStageKey());
                parseOptionRoutes(stage.getOptionRoutesJson()).values().stream()
                        .flatMap(route -> route.targets().stream())
                        .forEach(discoveredStages::add);
                iterator.remove();
                progressed = true;
            }
        } while (progressed && !remaining.isEmpty());

        if (!remaining.isEmpty()) {
            GameStage blocked = remaining.get(0);
            if (!discoveredStages.contains(blocked.getStableKey())) {
                throw new IllegalArgumentException("'" + blocked.getTitle()
                        + "' 문제로 이어지는 경로가 없습니다. 앞 문제의 ‘정답 후 이어질 문제’로 연결해 주세요.");
            }
            throw unreachableStage(blocked);
        }
    }

    private boolean hasRequirement(GameStage stage, Set<String> availableItems) {
        return availableItems.containsAll(stage.getRequiredItems());
    }

    private void grantReward(GameStage stage, Set<String> availableItems) {
        if (stage.isConsumeRequiredItems()) availableItems.removeAll(stage.getRequiredItems());
        if (stage.getRewardItem() != null) availableItems.add(stage.getRewardItem());
    }

    private IllegalArgumentException unreachableStage(GameStage stage) {
        return new IllegalArgumentException("'" + stage.getTitle()
                + "' 문제에 필요한 아이템을 먼저 얻을 방법이 없습니다. QR 단서로 발급하거나 앞서 풀 수 있는 문제의 보상으로 연결해 주세요.");
    }

    private List<String> parseOptions(String optionsText) {
        if (optionsText == null || optionsText.isBlank()) return List.of();
        String normalized = optionsText.replace("\r", "");
        String[] values = normalized.contains("\n") ? normalized.split("\n") : normalized.split(",");
        return Arrays.stream(values).map(String::trim).filter(value -> !value.isEmpty()).distinct().limit(20).toList();
    }

    private Map<String, ReleaseSnapshot.OptionRoute> parseOptionRoutes(String optionRoutesJson) {
        if (optionRoutesJson == null || optionRoutesJson.isBlank()) return Map.of();
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(optionRoutesJson);
            if (!root.isObject()) return Map.of();
            LinkedHashMap<String, ReleaseSnapshot.OptionRoute> routes = new LinkedHashMap<>();
            root.fields().forEachRemaining(entry -> {
                String option = entry.getKey().trim();
                ReleaseSnapshot.OptionRoute route = ReleaseSnapshot.OptionRoute.from(entry.getValue());
                if (!option.isEmpty() && !route.targets().isEmpty()) routes.put(option, route);
            });
            return Map.copyOf(routes);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("객관식 선택지 이동 경로 형식이 올바르지 않습니다.", e);
        }
    }

    private String toJson(ReleaseSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("발행본을 생성하지 못했습니다.", e);
        }
    }
}
