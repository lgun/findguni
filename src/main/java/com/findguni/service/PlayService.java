package com.findguni.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.findguni.model.*;
import com.findguni.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PlayService {
    private final EscapeGameRepository games;
    private final PlaySessionRepository sessions;
    private final PlayAttemptRepository attempts;
    private final ScannedClueRepository scannedClueRepository;
    private final PublishingService publishing;
    private final AnswerCodec answers;
    private final ObjectMapper objectMapper;

    public PlayService(EscapeGameRepository games, PlaySessionRepository sessions,
                       PlayAttemptRepository attempts, ScannedClueRepository scannedClueRepository,
                       PublishingService publishing, AnswerCodec answers, ObjectMapper objectMapper) {
        this.games = games;
        this.sessions = sessions;
        this.attempts = attempts;
        this.scannedClueRepository = scannedClueRepository;
        this.publishing = publishing;
        this.answers = answers;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public EscapeGame playableGame(String slug) {
        return games.findBySlugAndStatus(slug, GameStatus.PUBLISHED)
                .filter(EscapeGame::isPublished)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public ReleaseSnapshot publishedSnapshot(String slug) {
        EscapeGame game = playableGame(slug);
        return publishing.snapshot(game);
    }

    @Transactional(readOnly = true)
    public boolean hasActiveSession(String slug, String deviceHash) {
        EscapeGame game = games.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return sessions.findFirstByDeviceTokenHashAndRelease_Game_IdAndStatusOrderByLastActivityAtDesc(
                deviceHash, game.getId(), PlayStatus.ACTIVE).isPresent();
    }

    @Transactional
    public PlaySession startOrResume(String slug, String deviceHash) {
        EscapeGame game = playableGame(slug);
        GameRelease release = publishing.currentRelease(game);
        ReleaseSnapshot snapshot = publishing.snapshot(game);
        Optional<PlaySession> active = sessions
                .findFirstByDeviceTokenHashAndRelease_Game_IdAndStatusOrderByLastActivityAtDesc(
                        deviceHash, game.getId(), PlayStatus.ACTIVE);
        if (active.isPresent()) {
            return activeSessionOrRepair(active.get(), game, release, snapshot);
        }
        PlaySession session = new PlaySession(deviceHash, release);
        initializeSessionForSnapshot(session, snapshot);
        return sessions.save(session);
    }

    @Transactional
    public boolean openStagePreview(String slug, String deviceHash, String stageStableKey) {
        EscapeGame game = playableGame(slug);
        ReleaseSnapshot snapshot = publishing.snapshot(game);
        PlaySession session = startOrResume(slug, deviceHash);
        int stageIndex = -1;
        for (int i = 0; i < snapshot.stages().size(); i++) {
            if (Objects.equals(snapshot.stages().get(i).stableKey(), stageStableKey)) {
                stageIndex = i;
                break;
            }
        }
        if (stageIndex < 0) return false;

        session.setStatus(PlayStatus.ACTIVE);
        LinkedHashSet<String> solved = new LinkedHashSet<>();
        if (flowMode(snapshot) == GameFlowMode.QR_EXPLORATION) {
            LinkedHashSet<String> discovered = new LinkedHashSet<>();
            for (int i = 0; i < stageIndex; i++) {
                String previousStageKey = snapshot.stages().get(i).stableKey();
                solved.add(previousStageKey);
                discovered.add(previousStageKey);
            }
            discovered.add(stageStableKey);
            session.setSolvedStagesJson(writeKeys(solved));
            session.setDiscoveredStagesJson(writeKeys(discovered));
        } else {
            for (int i = 0; i < stageIndex; i++) {
                solved.add(snapshot.stages().get(i).stableKey());
            }
            session.setProgressIndex(stageIndex);
            session.setSolvedStagesJson(writeKeys(solved));
            session.setDiscoveredStagesJson(writeKeys(solved));
        }
        session.setActiveStageKey(stageStableKey);
        return true;
    }

    @Transactional(readOnly = true)
    public PlayView current(String slug, String deviceHash) {
        EscapeGame game = games.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        PlaySession session = sessions.findFirstByDeviceTokenHashAndRelease_Game_IdAndStatusOrderByLastActivityAtDesc(
                deviceHash, game.getId(), PlayStatus.ACTIVE)
                .or(() -> sessions.findFirstByDeviceTokenHashAndRelease_Game_IdOrderByLastActivityAtDesc(
                        deviceHash, game.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ReleaseSnapshot snapshot = publishing.snapshot(game);
        PlaySession activeSession = session.getStatus() == PlayStatus.ACTIVE
                ? activeSessionOrRepair(session, game, publishing.currentRelease(game), snapshot)
                : session;

        Set<String> solvedStages = stageKeys(activeSession.getSolvedStagesJson());
        Set<String> discoveredStageKeys = discoveredStageKeys(snapshot, activeSession);
        ReleaseSnapshot.StageSnapshot stage;
        if (flowMode(snapshot) == GameFlowMode.QR_EXPLORATION) {
            stage = snapshot.stages().stream()
                    .filter(candidate -> Objects.equals(candidate.stableKey(), activeSession.getActiveStageKey()))
                    .filter(candidate -> discoveredStageKeys.contains(candidate.stableKey()))
                    .filter(candidate -> !solvedStages.contains(candidate.stableKey()))
                    .findFirst().orElse(null);
        } else {
            stage = activeSession.getProgressIndex() < snapshot.stages().size()
                    ? snapshot.stages().get(activeSession.getProgressIndex()) : null;
            for (int i = 0; i < Math.min(activeSession.getProgressIndex(), snapshot.stages().size()); i++) {
                solvedStages.add(snapshot.stages().get(i).stableKey());
            }
        }
        List<ReleaseSnapshot.StageSnapshot> discoveredStages = snapshot.stages().stream()
                .filter(candidate -> discoveredStageKeys.contains(candidate.stableKey())).toList();
        return new PlayView(snapshot, activeSession, stage, inventoryItems(snapshot, inventoryKeys(activeSession)),
                scannedClues(snapshot, activeSession), activeSession.getNotes(), discoveredStages, Set.copyOf(solvedStages));
    }

    @Transactional
    public SolveResult solve(String slug, String deviceHash, String submittedAnswer) {
        EscapeGame game = games.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        GameRelease release = publishing.currentRelease(game);
        ReleaseSnapshot snapshot = publishing.snapshot(game);
        PlaySession session = activeSessionOrRepair(activeSession(deviceHash, game.getId()), game, release, snapshot);
        if (flowMode(snapshot) == GameFlowMode.QR_EXPLORATION) {
            return solveExploration(session, snapshot, submittedAnswer);
        }
        if (session.getProgressIndex() >= snapshot.stages().size()) {
            if (!session.isCompleted()) {
                session.complete();
            }
            return new SolveResult(true, true, null);
        }
        ReleaseSnapshot.StageSnapshot stage = snapshot.stages().get(session.getProgressIndex());
        LinkedHashSet<String> inventory = inventoryKeys(session);
        if (!inventory.containsAll(requiredItemKeys(stage))) {
            session.recordFailedAttempt();
            attempts.save(new PlayAttempt(session, stage.stableKey(), AttemptKind.SOLVE, false));
            return new SolveResult(false, false, "Required items are missing for this stage.");
        }
        boolean success = !stage.puzzleType().requiresAnswer()
                || hasOptionRoute(stage, submittedAnswer, inventory)
                || answers.matches(stage.puzzleType(), submittedAnswer, stage.answerDigest());
        attempts.save(new PlayAttempt(session, stage.stableKey(), AttemptKind.SOLVE, success));
        if (!success) {
            session.recordFailedAttempt();
            return new SolveResult(false, false, "Wrong answer. Try again.");
        }
        String selectedRoute = optionRoute(stage, submittedAnswer, inventory);
        applyItemExchange(session, stage, inventory);
        int routeIndex = stageIndex(snapshot, selectedRoute);
        if (routeIndex >= 0) {
            LinkedHashSet<String> routedSolved = new LinkedHashSet<>();
            for (int i = 0; i < routeIndex; i++) {
                routedSolved.add(snapshot.stages().get(i).stableKey());
            }
            session.recordSuccessfulAttempt();
            session.setSolvedStagesJson(writeKeys(routedSolved));
            session.setDiscoveredStagesJson(writeKeys(routedSolved));
            session.setProgressIndex(routeIndex);
            session.setActiveStageKey(selectedRoute);
        } else {
            session.advance();
        }
        boolean completed = session.getProgressIndex() >= snapshot.stages().size();
        if (completed) session.complete();
        return new SolveResult(true, completed, null);
    }

    @Transactional
    public HintRevealResult revealHint(String slug, String deviceHash) {
        EscapeGame game = games.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        GameRelease release = publishing.currentRelease(game);
        ReleaseSnapshot snapshot = publishing.snapshot(game);
        PlaySession session = activeSessionOrRepair(activeSession(deviceHash, game.getId()), game, release, snapshot);
        ReleaseSnapshot.StageSnapshot stage;
        if (flowMode(snapshot) == GameFlowMode.QR_EXPLORATION) {
            stage = snapshot.stages().stream()
                    .filter(candidate -> Objects.equals(candidate.stableKey(), session.getActiveStageKey()))
                    .findFirst().orElse(null);
            if (stage == null) return new HintRevealResult(false, false, "", "No active stage found.");
        } else {
            if (session.getProgressIndex() >= snapshot.stages().size()) {
                return new HintRevealResult(false, false, "", "No current stage.");
            }
            stage = snapshot.stages().get(session.getProgressIndex());
        }

        HintAvailability availability = hintAvailability(snapshot, session, stage);
        if (availability.alreadyRevealed()) {
            return new HintRevealResult(true, false, stage.hint(), "Already revealed.");
        }
        if (!availability.allowed()) {
            return new HintRevealResult(false, false, "", availability.statusText());
        }

        LinkedHashSet<String> revealedHints = stageKeys(session.getRevealedHintsJson());
        revealedHints.add(stage.stableKey());
        session.recordHint(writeKeys(revealedHints));
        attempts.save(new PlayAttempt(session, stage.stableKey(), AttemptKind.HINT, true));
        return new HintRevealResult(true, true, stage.hint(), "Hint unlocked.");
    }

    public HintAvailability hintAvailability(PlayView view) {
        return hintAvailability(view.game(), view.session(), view.stage());
    }

    private HintAvailability hintAvailability(ReleaseSnapshot snapshot, PlaySession session,
                                              ReleaseSnapshot.StageSnapshot stage) {
        boolean unlimited = snapshot.isUnlimitedHints();
        int limit = Math.max(1, snapshot.getHintLimit());
        int remaining = unlimited ? -1 : Math.max(0, limit - session.getHintsUsed());
        int cooldown = Math.max(0, snapshot.getHintCooldownSeconds());
        boolean hasHint = stage != null && stage.hint() != null && !stage.hint().isBlank();
        boolean alreadyRevealed = stage != null
                && stageKeys(session.getRevealedHintsJson()).contains(stage.stableKey());
        int retryAfterSeconds = 0;
        if (!alreadyRevealed && cooldown > 0 && session.getLastHintAt() != null) {
            long remainingMillis = Duration.between(Instant.now(), session.getLastHintAt().plusSeconds(cooldown)).toMillis();
            retryAfterSeconds = remainingMillis <= 0 ? 0 : (int) Math.ceil(remainingMillis / 1_000.0);
        }

        boolean allowed = hasHint && (alreadyRevealed
                || ((unlimited || remaining > 0) && retryAfterSeconds == 0));
        String statusText;
        if (!hasHint) statusText = "This stage does not have a hint.";
        else if (alreadyRevealed) statusText = unlimited
                ? "Hint already revealed."
                : "Hint already revealed. Remaining hint count: " + remaining + ".";
        else if (!unlimited && remaining == 0) statusText = "힌트는 모두 썼습니다.";
        else if (retryAfterSeconds > 0) statusText = retryAfterSeconds + "초 후 다시 사용 가능합니다.";
        else if (unlimited) statusText = cooldown > 0
                ? "Unlimited mode. Available again after " + cooldown + " seconds."
                : "Unlimited hints available.";
        else statusText = "Remaining hints: " + remaining + ".";
        return new HintAvailability(allowed, hasHint, alreadyRevealed, unlimited,
                remaining, cooldown, retryAfterSeconds, statusText);
    }

    @Transactional
    public PlaySession restart(String slug, String deviceHash) {
        EscapeGame game = playableGame(slug);
        sessions.findFirstByDeviceTokenHashAndRelease_Game_IdAndStatusOrderByLastActivityAtDesc(
                deviceHash, game.getId(), PlayStatus.ACTIVE).ifPresent(PlaySession::abandon);
        GameRelease release = publishing.currentRelease(game);
        ReleaseSnapshot snapshot = publishing.snapshot(game);
        PlaySession session = new PlaySession(deviceHash, release);
        initializeSessionForSnapshot(session, snapshot);
        return sessions.save(session);
    }

    @Transactional
    public void saveNotes(String slug, String deviceHash, String notes) {
        EscapeGame game = games.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        GameRelease release = publishing.currentRelease(game);
        ReleaseSnapshot snapshot = publishing.snapshot(game);
        PlaySession session = activeSessionOrRepair(activeSession(deviceHash, game.getId()), game, release, snapshot);
        if (!snapshot.allowNotebook()) throw new IllegalArgumentException("Notes are not enabled for this game.");
        String safeNotes = Objects.toString(notes, "");
        if (safeNotes.length() > 20_000) throw new IllegalArgumentException("Notes must be shorter than 20,000 characters.");
        session.setNotes(safeNotes);
    }

    @Transactional
    public ClueScanResult scanClue(String slug, String deviceHash, String itemStableKey) {
        return scanClueInternal(slug, deviceHash, itemStableKey, true);
    }

    @Transactional
    public ClueScanResult scanClueFromLink(String slug, String deviceHash, String itemStableKey) {
        return scanClueInternal(slug, deviceHash, itemStableKey, false);
    }

    @Transactional
    public QrScanResult scanStage(String slug, String deviceHash, String stageStableKey,
                                  boolean requireScannerEnabled) {
        EscapeGame game = games.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        GameRelease release = publishing.currentRelease(game);
        ReleaseSnapshot snapshot = publishing.snapshot(game);
        PlaySession session = activeSessionOrRepair(activeSession(deviceHash, game.getId()), game, release, snapshot);
        if (requireScannerEnabled && !snapshot.allowQrScanner()) {
            return new QrScanResult(false, false, false, "QR scan is not available for this game.", "STAGE", null, null);
        }
        if (flowMode(snapshot) != GameFlowMode.QR_EXPLORATION) {
            return new QrScanResult(true, false, false,
                    "This game is not in QR exploration mode.", "STAGE", null, null);
        }
        ReleaseSnapshot.StageSnapshot stage = snapshot.stages().stream()
                .filter(candidate -> candidate.stableKey().equals(stageStableKey)).findFirst().orElse(null);
        if (stage == null) return new QrScanResult(false, false, false,
                "This stage does not exist in current game setup.", "STAGE", null, null);
        if (entryMode(stage) != StageEntryMode.QR) {
            return new QrScanResult(true, false, false,
                    "Only QR stages can be entered by QR scan.", "STAGE", null, null);
        }
        Set<String> solved = stageKeys(session.getSolvedStagesJson());
        if (solved.contains(stageStableKey)) {
            return new QrScanResult(true, true, true, "This stage is already solved.", "STAGE", null, null);
        }
        LinkedHashSet<String> discovered = stageKeys(session.getDiscoveredStagesJson());
        boolean isNew = discovered.add(stageStableKey);
        session.setDiscoveredStagesJson(writeKeys(discovered));
        session.setActiveStageKey(stageStableKey);
        String message = isNew ? "New stage discovered." : "The stage is already discovered.";
        String redirectUrl = "/play/" + game.getSlug() + "/puzzle/" + stageStableKey;
        return new QrScanResult(true, true, true, message, "STAGE", redirectUrl, stageStableKey);
    }

    @Transactional
    public boolean selectDiscoveredStage(String slug, String deviceHash, String stageStableKey) {
        EscapeGame game = games.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        GameRelease release = publishing.currentRelease(game);
        ReleaseSnapshot snapshot = publishing.readSnapshot(release);
        PlaySession session = activeSessionOrRepair(activeSession(deviceHash, game.getId()), game, release, snapshot);
        if (flowMode(snapshot) != GameFlowMode.QR_EXPLORATION) return false;
        ReleaseSnapshot.StageSnapshot stage = snapshot.stages().stream()
                .filter(candidate -> candidate.stableKey().equals(stageStableKey)).findFirst().orElse(null);
        if (stage == null || stageKeys(session.getSolvedStagesJson()).contains(stageStableKey)) return false;
        Set<String> discovered = discoveredStageKeys(snapshot, session);
        if (!discovered.contains(stageStableKey)) return false;
        session.setActiveStageKey(stageStableKey);
        return true;
    }

    @Transactional(readOnly = true)
    public PlaySummary summary(String slug, String deviceHash) {
        PlayView view = current(slug, deviceHash);
        PlaySession session = view.session();
        long minutes = Math.max(1, Duration.between(session.getStartedAt(),
                session.getCompletedAt() == null ? session.getLastActivityAt() : session.getCompletedAt()).toMinutes());
        return new PlaySummary(view.game().title(), session.getAttemptCount(), session.getHintsUsed(),
                minutes, session.getStartedAt(), session.getCompletedAt());
    }

    private boolean hasOptionRoute(ReleaseSnapshot.StageSnapshot stage, String submittedAnswer,
                                   Set<String> inventory) {
        return optionRoute(stage, submittedAnswer, inventory) != null;
    }

    private String optionRoute(ReleaseSnapshot.StageSnapshot stage, String submittedAnswer,
                               Set<String> inventory) {
        if (stage == null || stage.puzzleType() != PuzzleType.MULTIPLE_CHOICE || stage.getOptionRoutes().isEmpty()) {
            return null;
        }
        ReleaseSnapshot.OptionRoute route = stage.getOptionRoutes()
                .get(Objects.toString(submittedAnswer, "").trim());
        return route == null ? null : route.resolve(inventory);
    }

    private int stageIndex(ReleaseSnapshot snapshot, String stableKey) {
        if (stableKey == null) return -1;
        for (int i = 0; i < snapshot.stages().size(); i++) {
            if (Objects.equals(snapshot.stages().get(i).stableKey(), stableKey)) return i;
        }
        return -1;
    }

    private SolveResult solveExploration(PlaySession session, ReleaseSnapshot snapshot, String submittedAnswer) {
        ReleaseSnapshot.StageSnapshot stage = snapshot.stages().stream()
                .filter(candidate -> Objects.equals(candidate.stableKey(), session.getActiveStageKey()))
                .findFirst().orElse(null);
        if (stage == null) {
            return new SolveResult(false, false, "Invalid active stage.");
        }
        Set<String> discovered = discoveredStageKeys(snapshot, session);
        if (!discovered.contains(stage.stableKey())) {
            return new SolveResult(false, false, "Current stage has not been discovered yet.");
        }
        LinkedHashSet<String> solved = stageKeys(session.getSolvedStagesJson());
        if (solved.contains(stage.stableKey())) {
            session.setActiveStageKey(null);
            boolean completed = solved.size() >= snapshot.stages().size();
            if (completed) session.complete();
            return new SolveResult(true, completed, null);
        }
        LinkedHashSet<String> inventory = inventoryKeys(session);
        if (!inventory.containsAll(requiredItemKeys(stage))) {
            session.recordFailedAttempt();
            attempts.save(new PlayAttempt(session, stage.stableKey(), AttemptKind.SOLVE, false));
            return new SolveResult(false, false, "필요한 아이템이 아직 없습니다.");
        }
        boolean success = !stage.puzzleType().requiresAnswer()
                || hasOptionRoute(stage, submittedAnswer, inventory)
                || answers.matches(stage.puzzleType(), submittedAnswer, stage.answerDigest());
        attempts.save(new PlayAttempt(session, stage.stableKey(), AttemptKind.SOLVE, success));
        if (!success) {
            session.recordFailedAttempt();
            return new SolveResult(false, false, "Wrong answer. Try again.");
        }
        String selectedRoute = optionRoute(stage, submittedAnswer, inventory);
        applyItemExchange(session, stage, inventory);
        session.recordSuccessfulAttempt();
        solved.add(stage.stableKey());
        if (selectedRoute != null) solved.remove(selectedRoute);
        session.setSolvedStagesJson(writeKeys(solved));
        ReleaseSnapshot.StageSnapshot nextStage = snapshot.stages().stream()
                .filter(candidate -> Objects.equals(candidate.stableKey(),
                        selectedRoute == null ? stage.nextStageKey() : selectedRoute))
                .filter(candidate -> !solved.contains(candidate.stableKey()))
                .findFirst().orElse(null);
        if (nextStage != null) {
            LinkedHashSet<String> newlyDiscovered = stageKeys(session.getDiscoveredStagesJson());
            newlyDiscovered.add(nextStage.stableKey());
            session.setDiscoveredStagesJson(writeKeys(newlyDiscovered));
            session.setActiveStageKey(nextStage.stableKey());
        } else {
            session.setActiveStageKey(null);
        }
        boolean completed = solved.size() >= snapshot.stages().size();
        if (completed) {
            session.complete();
        }
        String message = completed
                ? "All stages complete."
                : null;
        return new SolveResult(true, completed, message);
    }

    private PlaySession activeSession(String deviceHash, Long gameId) {
        return sessions.findFirstByDeviceTokenHashAndRelease_Game_IdAndStatusOrderByLastActivityAtDesc(
                deviceHash, gameId, PlayStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private ClueScanResult scanClueInternal(String slug, String deviceHash, String itemStableKey,
                                            boolean requireScannerEnabled) {
        EscapeGame game = games.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        GameRelease release = publishing.currentRelease(game);
        ReleaseSnapshot snapshot = publishing.snapshot(game);
        PlaySession session = activeSessionOrRepair(activeSession(deviceHash, game.getId()), game, release, snapshot);
        if (requireScannerEnabled && !snapshot.allowQrScanner()) {
            return new ClueScanResult(false, false, false, "QR scanning is not available for this game.", null);
        }
        ReleaseSnapshot.ItemSnapshot item = snapshot.items().stream()
                .filter(candidate -> candidate.stableKey().equals(itemStableKey)).findFirst().orElse(null);
        if (item == null) return new ClueScanResult(false, false, false, "Item not found.", null);
        if (!item.qrEnabled()) return new ClueScanResult(true, false, false, "This item does not support QR scan.", null);
        LinkedHashSet<String> inventory = inventoryKeys(session);
        if (item.alternateRequiredItem() != null
                && inventory.contains(item.alternateRequiredItem())
                && item.alternateScanText() != null
                && !item.alternateScanText().isBlank()) {
            session.touch();
            return new ClueScanResult(true, true, true, item.alternateScanText(), item);
        }
        boolean isNew = scannedClueRepository.findByPlaySessionIdAndItemStableKey(session.getId(), itemStableKey).isEmpty();
        if (isNew) scannedClueRepository.save(new ScannedClue(session, itemStableKey));
        if (!consumedItemKeys(session).contains(itemStableKey) && inventory.add(itemStableKey)) {
            session.setInventoryJson(writeInventory(inventory));
        }
        session.touch();
        String message = isNew ? "You found a clue." : "You already found this clue.";
        String redirectUrl = "/play/" + game.getSlug() + "/clue/" + itemStableKey;
        return new ClueScanResult(true, true, true, message, item)
                .withRedirectUrl(redirectUrl); // compile: placeholder below via custom record? replace below
    }

    private List<ScannedClueView> scannedClues(ReleaseSnapshot snapshot, PlaySession session) {
        if (!snapshot.allowCluebook()) return List.of();
        Map<String, ReleaseSnapshot.ItemSnapshot> itemByKey = new LinkedHashMap<>();
        snapshot.items().forEach(item -> itemByKey.put(item.stableKey(), item));
        return scannedClueRepository.findAllByPlaySessionIdOrderByScannedAtAsc(session.getId()).stream()
                .filter(clue -> itemByKey.containsKey(clue.getItemStableKey()))
                .map(clue -> new ScannedClueView(itemByKey.get(clue.getItemStableKey()), clue.getScannedAt()))
                .toList();
    }

    private LinkedHashSet<String> inventoryKeys(PlaySession session) {
        return readKeys(session.getInventoryJson());
    }

    private LinkedHashSet<String> consumedItemKeys(PlaySession session) {
        return readKeys(session.getConsumedItemsJson());
    }

    private LinkedHashSet<String> stageKeys(String json) {
        return readKeys(json);
    }

    private LinkedHashSet<String> readKeys(String json) {
        try {
            List<String> values = objectMapper.readValue(Objects.toString(json, "[]"), new TypeReference<>() {});
            return new LinkedHashSet<>(values);
        } catch (JsonProcessingException e) {
            return new LinkedHashSet<>();
        }
    }

    private Set<String> discoveredStageKeys(ReleaseSnapshot snapshot, PlaySession session) {
        LinkedHashSet<String> discovered = stageKeys(session.getDiscoveredStagesJson());
        snapshot.stages().stream().filter(stage -> entryMode(stage) == StageEntryMode.START)
                .map(ReleaseSnapshot.StageSnapshot::stableKey).forEach(discovered::add);
        return discovered;
    }

    private void initializeSessionForSnapshot(PlaySession session, ReleaseSnapshot snapshot) {
        LinkedHashSet<String> initialInventory = new LinkedHashSet<>();
        snapshot.items().stream().filter(ReleaseSnapshot.ItemSnapshot::initiallyOwned)
                .map(ReleaseSnapshot.ItemSnapshot::stableKey).forEach(initialInventory::add);
        session.setInventoryJson(writeInventory(initialInventory));
        session.setConsumedItemsJson("[]");
        session.setDiscoveredStagesJson("[]");
        session.setSolvedStagesJson("[]");
        session.setRevealedHintsJson("[]");
        session.setProgressIndex(0);
        session.setActiveStageKey(null);
        if (flowMode(snapshot) != GameFlowMode.QR_EXPLORATION) {
            return;
        }
        LinkedHashSet<String> discovered = new LinkedHashSet<>();
        snapshot.stages().stream()
                .filter(stage -> entryMode(stage) == StageEntryMode.START)
                .map(ReleaseSnapshot.StageSnapshot::stableKey)
                .forEach(discovered::add);
        session.setDiscoveredStagesJson(writeKeys(discovered));
        snapshot.stages().stream()
                .filter(stage -> entryMode(stage) == StageEntryMode.START)
                .findFirst()
                .ifPresent(stage -> session.setActiveStageKey(stage.stableKey()));
    }

    private PlaySession activeSessionOrRepair(PlaySession session, EscapeGame game, GameRelease release,
                                             ReleaseSnapshot snapshot) {
        if (session == null) return null;
        boolean repaired = false;
        if (flowMode(snapshot) == GameFlowMode.QR_EXPLORATION) {
            repaired = repairQrSession(session, snapshot);
        } else {
            repaired = repairLinearSession(session, snapshot);
        }
        if (session.getRelease() == null || !session.getRelease().getId().equals(release.getId())) {
            session.setRelease(release);
            repaired = true;
        }
        if (repaired) session.touch();
        return session;
    }

    private boolean repairLinearSession(PlaySession session, ReleaseSnapshot snapshot) {
        Set<String> stageKeys = snapshot.stages().stream()
                .map(ReleaseSnapshot.StageSnapshot::stableKey).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> itemKeys = snapshot.items().stream()
                .map(ReleaseSnapshot.ItemSnapshot::stableKey).collect(Collectors.toCollection(LinkedHashSet::new));

        LinkedHashSet<String> solved = stageKeys(session.getSolvedStagesJson());
        LinkedHashSet<String> discovered = stageKeys(session.getDiscoveredStagesJson());
        LinkedHashSet<String> inventory = inventoryKeys(session);
        LinkedHashSet<String> consumed = consumedItemKeys(session);
        LinkedHashSet<String> hints = stageKeys(session.getRevealedHintsJson());

        boolean repaired = trimToValidKeys(solved, stageKeys);
        repaired = trimToValidKeys(discovered, stageKeys) || repaired;
        repaired = trimToValidKeys(inventory, itemKeys) || repaired;
        repaired = trimToValidKeys(consumed, itemKeys) || repaired;
        repaired = trimToValidKeys(hints, stageKeys) || repaired;
        repaired = restoreEarnedInventory(session, snapshot, solved, inventory, consumed) || repaired;

        LinkedHashSet<String> orderedSolved = new LinkedHashSet<>();
        for (ReleaseSnapshot.StageSnapshot stage : snapshot.stages()) {
            if (solved.contains(stage.stableKey())) {
                orderedSolved.add(stage.stableKey());
            } else {
                break;
            }
        }

        int progressFromSession = Math.max(0, session.getProgressIndex());
        int targetProgress = Math.min(progressFromSession, snapshot.stages().size());
        if (solved.isEmpty() && targetProgress > 0) {
            for (int i = 0; i < targetProgress; i++) {
                orderedSolved.add(snapshot.stages().get(i).stableKey());
            }
            solved = orderedSolved;
            repaired = true;
        }

        if (orderedSolved.size() != solved.size()) {
            solved = orderedSolved;
            repaired = true;
        }

        int progressCandidate = Math.max(targetProgress, orderedSolved.size());
        if (session.getProgressIndex() != progressCandidate) {
            session.setProgressIndex(progressCandidate);
            repaired = true;
        }
        if (!orderedSolved.equals(solved)) {
            solved = orderedSolved;
            repaired = true;
        }
        String expectedActive = snapshot.stages().size() > progressCandidate
                ? snapshot.stages().get(progressCandidate).stableKey()
                : null;
        if (!Objects.equals(session.getActiveStageKey(), expectedActive)) {
            session.setActiveStageKey(expectedActive);
            repaired = true;
        }

        String solvedJson = writeKeys(solved);
        String discoveredJson = writeKeys(solved);
        String inventoryJson = writeInventory(inventory);
        String consumedJson = writeKeys(consumed);
        String hintsJson = writeKeys(hints);
        if (!solvedJson.equals(session.getSolvedStagesJson())) {
            session.setSolvedStagesJson(solvedJson);
            repaired = true;
        }
        if (!discoveredJson.equals(session.getDiscoveredStagesJson())) {
            session.setDiscoveredStagesJson(discoveredJson);
            repaired = true;
        }
        if (!inventoryJson.equals(session.getInventoryJson())) {
            session.setInventoryJson(inventoryJson);
            repaired = true;
        }
        if (!consumedJson.equals(session.getConsumedItemsJson())) {
            session.setConsumedItemsJson(consumedJson);
            repaired = true;
        }
        if (!hintsJson.equals(session.getRevealedHintsJson())) {
            session.setRevealedHintsJson(hintsJson);
            repaired = true;
        }
        if (session.getSolvedStagesJson().isEmpty() && session.getDiscoveredStagesJson().isEmpty()
                && snapshot.flowMode() == GameFlowMode.LINEAR && !session.isCompleted()) {
            repaired = true;
        }
        return repaired;
    }

    private boolean repairQrSession(PlaySession session, ReleaseSnapshot snapshot) {
        Set<String> stageKeys = snapshot.stages().stream()
                .map(ReleaseSnapshot.StageSnapshot::stableKey).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> itemKeys = snapshot.items().stream()
                .map(ReleaseSnapshot.ItemSnapshot::stableKey).collect(Collectors.toCollection(LinkedHashSet::new));

        LinkedHashSet<String> solved = stageKeys(session.getSolvedStagesJson());
        LinkedHashSet<String> discovered = stageKeys(session.getDiscoveredStagesJson());
        LinkedHashSet<String> inventory = inventoryKeys(session);
        LinkedHashSet<String> consumed = consumedItemKeys(session);
        LinkedHashSet<String> hints = stageKeys(session.getRevealedHintsJson());

        boolean repaired = trimToValidKeys(solved, stageKeys);
        repaired = trimToValidKeys(discovered, stageKeys) || repaired;
        repaired = trimToValidKeys(inventory, itemKeys) || repaired;
        repaired = trimToValidKeys(consumed, itemKeys) || repaired;
        repaired = trimToValidKeys(hints, stageKeys) || repaired;
        repaired = restoreEarnedInventory(session, snapshot, solved, inventory, consumed) || repaired;

        LinkedHashSet<String> orderedSolved = new LinkedHashSet<>();
        for (ReleaseSnapshot.StageSnapshot stage : snapshot.stages()) {
            if (solved.contains(stage.stableKey())) {
                orderedSolved.add(stage.stableKey());
            }
        }
        if (!orderedSolved.equals(solved)) {
            solved = orderedSolved;
            repaired = true;
        }

        LinkedHashSet<String> repairedDiscovered = new LinkedHashSet<>();
        snapshot.stages().stream().filter(stage -> entryMode(stage) == StageEntryMode.START)
                .map(ReleaseSnapshot.StageSnapshot::stableKey).forEach(repairedDiscovered::add);
        snapshot.stages().stream()
                .filter(stage -> discovered.contains(stage.stableKey()))
                .forEach(stage -> repairedDiscovered.add(stage.stableKey()));
        LinkedHashSet<String> normalizedDiscovered = repairedDiscovered.equals(discovered) ? discovered : repairedDiscovered;
        repaired = !repairedDiscovered.equals(discovered) || repaired;

        String currentActive = session.getActiveStageKey();
        boolean currentActiveIsValid = currentActive != null
                && stageKeys.contains(currentActive)
                && normalizedDiscovered.contains(currentActive)
                && !solved.contains(currentActive);
        String preferredActive = currentActiveIsValid
                ? currentActive
                : chooseActiveStage(snapshot, solved, normalizedDiscovered);
        if (!Objects.equals(session.getActiveStageKey(), preferredActive)) {
            session.setActiveStageKey(preferredActive);
            repaired = true;
        }
        if (session.getProgressIndex() > snapshot.stages().size()) {
            session.setProgressIndex(Math.min(Math.max(session.getProgressIndex(), 0), snapshot.stages().size()));
            repaired = true;
        }
        String solvedJson = writeKeys(solved);
        String discoveredJson = writeKeys(normalizedDiscovered);
        String inventoryJson = writeInventory(inventory);
        String consumedJson = writeKeys(consumed);
        String hintsJson = writeKeys(hints);
        if (!solvedJson.equals(session.getSolvedStagesJson())) {
            session.setSolvedStagesJson(solvedJson);
            repaired = true;
        }
        if (!discoveredJson.equals(session.getDiscoveredStagesJson())) {
            session.setDiscoveredStagesJson(discoveredJson);
            repaired = true;
        }
        if (!inventoryJson.equals(session.getInventoryJson())) {
            session.setInventoryJson(inventoryJson);
            repaired = true;
        }
        if (!consumedJson.equals(session.getConsumedItemsJson())) {
            session.setConsumedItemsJson(consumedJson);
            repaired = true;
        }
        if (!hintsJson.equals(session.getRevealedHintsJson())) {
            session.setRevealedHintsJson(hintsJson);
            repaired = true;
        }
        return repaired;
    }

    private boolean restoreEarnedInventory(PlaySession session, ReleaseSnapshot snapshot,
                                           Set<String> solved, Set<String> inventory, Set<String> consumed) {
        LinkedHashSet<String> earned = new LinkedHashSet<>();
        snapshot.items().stream().filter(ReleaseSnapshot.ItemSnapshot::initiallyOwned)
                .map(ReleaseSnapshot.ItemSnapshot::stableKey).forEach(earned::add);
        if (session.getId() != null) {
            scannedClueRepository.findAllByPlaySessionIdOrderByScannedAtAsc(session.getId()).stream()
                    .map(ScannedClue::getItemStableKey).forEach(earned::add);
        }
        snapshot.stages().stream().filter(stage -> solved.contains(stage.stableKey()))
                .map(ReleaseSnapshot.StageSnapshot::rewardItem)
                .filter(Objects::nonNull).forEach(earned::add);
        earned.removeAll(consumed);
        return inventory.addAll(earned);
    }

    private String chooseActiveStage(ReleaseSnapshot snapshot, Set<String> solved, Set<String> discovered) {
        for (ReleaseSnapshot.StageSnapshot stage : snapshot.stages()) {
            if (!solved.contains(stage.stableKey()) && discovered.contains(stage.stableKey())) {
                return stage.stableKey();
            }
        }
        return null;
    }

    private boolean trimToValidKeys(LinkedHashSet<String> values, Set<String> valid) {
        return values.removeIf(value -> !valid.contains(value));
    }

    private StageEntryMode entryMode(ReleaseSnapshot.StageSnapshot stage) {
        if (stage.entryMode() != null) return stage.entryMode();
        return stage.qrEnabled() ? StageEntryMode.QR : StageEntryMode.START;
    }

    private GameFlowMode flowMode(ReleaseSnapshot snapshot) {
        return snapshot.flowMode() == null ? GameFlowMode.LINEAR : snapshot.flowMode();
    }

    private String writeInventory(Set<String> inventory) {
        return writeKeys(inventory);
    }

    private String writeKeys(Set<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize JSON.", e);
        }
    }

    private List<ReleaseSnapshot.ItemSnapshot> inventoryItems(ReleaseSnapshot snapshot, Set<String> keys) {
        return snapshot.items().stream().filter(item -> keys.contains(item.stableKey())).toList();
    }

    public List<RequiredItemView> requiredItemViews(PlayView view) {
        if (view == null || view.stage() == null) return List.of();
        Set<String> inventory = view.inventory().stream()
                .map(ReleaseSnapshot.ItemSnapshot::stableKey)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, ReleaseSnapshot.ItemSnapshot> items = new LinkedHashMap<>();
        view.game().items().forEach(item -> items.put(item.stableKey(), item));
        return requiredItemKeys(view.stage()).stream()
                .map(key -> new RequiredItemView(items.get(key), inventory.contains(key)))
                .filter(required -> required.item() != null)
                .toList();
    }

    private List<String> requiredItemKeys(ReleaseSnapshot.StageSnapshot stage) {
        return stage == null ? List.of() : stage.getRequiredItems();
    }

    private void applyItemExchange(PlaySession session, ReleaseSnapshot.StageSnapshot stage,
                                   LinkedHashSet<String> inventory) {
        if (stage.consumeRequiredItems()) {
            List<String> required = requiredItemKeys(stage);
            inventory.removeAll(required);
            LinkedHashSet<String> consumed = consumedItemKeys(session);
            consumed.addAll(required);
            session.setConsumedItemsJson(writeKeys(consumed));
        }
        if (stage.rewardItem() != null) inventory.add(stage.rewardItem());
        session.setInventoryJson(writeInventory(inventory));
    }

    public record PlayView(ReleaseSnapshot game, PlaySession session,
                           ReleaseSnapshot.StageSnapshot stage,
                           List<ReleaseSnapshot.ItemSnapshot> inventory,
                           List<ScannedClueView> scannedClues,
                           String notes,
                           List<ReleaseSnapshot.StageSnapshot> discoveredStages,
                           Set<String> solvedStageKeys) {
        public ReleaseSnapshot getGame() { return game; }
        public PlaySession getSession() { return session; }
        public ReleaseSnapshot.StageSnapshot getStage() { return stage; }
        public List<ReleaseSnapshot.ItemSnapshot> getInventory() { return inventory; }
        public List<ScannedClueView> getScannedClues() { return scannedClues; }
        public String getNotes() { return notes; }
        public List<ReleaseSnapshot.StageSnapshot> getDiscoveredStages() { return discoveredStages; }
        public Set<String> getSolvedStageKeys() { return solvedStageKeys; }
        public int getSolvedStageCount() { return solvedStageKeys.size(); }
    }

    public record RequiredItemView(ReleaseSnapshot.ItemSnapshot item, boolean owned) {
        public ReleaseSnapshot.ItemSnapshot getItem() { return item; }
        public boolean isOwned() { return owned; }
    }

    public record ScannedClueView(ReleaseSnapshot.ItemSnapshot item, Instant scannedAt) {
        public ReleaseSnapshot.ItemSnapshot getItem() { return item; }
        public Instant getScannedAt() { return scannedAt; }
        public String getStableKey() { return item.stableKey(); }
        public String getName() { return item.name(); }
        public String getDescription() { return item.description(); }
        public String getEmoji() { return item.emoji(); }
        public String getImageUrl() { return item.imageUrl(); }
        public String getClueText() { return item.clueText(); }
    }

    public record ClueScanResult(boolean found, boolean accepted, boolean success,
                                 String message, ReleaseSnapshot.ItemSnapshot item, String redirectUrl) {
        public ClueScanResult(boolean found, boolean accepted, boolean success, String message,
                             ReleaseSnapshot.ItemSnapshot item) {
            this(found, accepted, success, message, item, null);
        }

        public ClueScanResult withRedirectUrl(String redirectUrl) {
            return new ClueScanResult(found, accepted, success, message, item, redirectUrl);
        }

        public boolean isFound() { return found; }
        public boolean isAccepted() { return accepted; }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public ReleaseSnapshot.ItemSnapshot getItem() { return item; }
        public String getRedirectUrl() { return redirectUrl; }
    }

    public record QrScanResult(boolean found, boolean accepted, boolean success,
                               String message, String targetType, String redirectUrl,
                               String itemStableKey, Item item) {
        public QrScanResult(boolean found, boolean accepted, boolean success,
                            String message, String targetType, String redirectUrl, String itemStableKey) {
            this(found, accepted, success, message, targetType, redirectUrl, itemStableKey,
                    itemStableKey == null ? null : new Item(itemStableKey, null, null, null, null, null, null));
        }

        public record Item(String stableKey, String name, String description, String clueText,
                           String emoji, String imageUrl, String copyableText) {}

        public static QrScanResult clue(ClueScanResult result) {
            ReleaseSnapshot.ItemSnapshot item = result.item();
            return new QrScanResult(result.found(), result.accepted(), result.success(),
                    result.message(), "CLUE", result.redirectUrl(), item == null ? null : item.stableKey(),
                    item == null ? null : new Item(item.stableKey(), item.name(), item.description(), item.clueText(),
                            item.emoji(), item.imageUrl(), item.copyableText()));
        }

        public String getItemStableKey() {
            return itemStableKey;
        }

        public Item getItem() { return item; }
    }

    public record SolveResult(boolean success, boolean completed, String message) {
        public boolean isSuccess() { return success; }
        public boolean isCompleted() { return completed; }
        public String getMessage() { return message; }
    }

    public record HintAvailability(boolean allowed, boolean hasHint, boolean alreadyRevealed,
                                   boolean unlimited, int remainingHints, int cooldownSeconds,
                                   int retryAfterSeconds, String statusText) {
        public boolean isAllowed() { return allowed; }
        public boolean isHasHint() { return hasHint; }
        public boolean isAlreadyRevealed() { return alreadyRevealed; }
        public boolean isUnlimited() { return unlimited; }
        public int getRemainingHints() { return remainingHints; }
        public int getCooldownSeconds() { return cooldownSeconds; }
        public int getRetryAfterSeconds() { return retryAfterSeconds; }
        public String getStatusText() { return statusText; }
    }

    public record HintRevealResult(boolean revealed, boolean newlyConsumed,
                                   String hint, String message) {
        public boolean isRevealed() { return revealed; }
        public boolean isNewlyConsumed() { return newlyConsumed; }
        public String getHint() { return hint; }
        public String getMessage() { return message; }
    }

    public record PlaySummary(String title, int attemptCount, int hintsUsed, long elapsedMinutes,
                               Instant startedAt, Instant completedAt) {
        public String getTitle() { return title; }
        public int getAttemptCount() { return attemptCount; }
        public int getHintsUsed() { return hintsUsed; }
        public long getElapsedMinutes() { return elapsedMinutes; }
        public Instant getStartedAt() { return startedAt; }
        public Instant getCompletedAt() { return completedAt; }
        public int getHintCount() { return hintsUsed; }
        public String getElapsedDisplay() {
            long hours = elapsedMinutes / 60;
            long minutes = elapsedMinutes % 60;
            return hours > 0 ? String.format("%dh %02dm", hours, minutes) : String.format("%dm", minutes);
        }
    }
}
