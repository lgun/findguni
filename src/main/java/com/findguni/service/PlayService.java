package com.findguni.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.findguni.model.*;
import com.findguni.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.util.*;

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
                       PlayAttemptRepository attempts, ScannedClueRepository scannedClueRepository, PublishingService publishing,
                       AnswerCodec answers, ObjectMapper objectMapper) {
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
    public boolean hasActiveSession(String slug, String deviceHash) {
        EscapeGame game = games.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return sessions.findFirstByDeviceTokenHashAndRelease_Game_IdAndStatusOrderByLastActivityAtDesc(
                deviceHash, game.getId(), PlayStatus.ACTIVE).isPresent();
    }

    @Transactional
    public PlaySession startOrResume(String slug, String deviceHash) {
        EscapeGame game = playableGame(slug);
        Optional<PlaySession> active = sessions
                .findFirstByDeviceTokenHashAndRelease_Game_IdAndStatusOrderByLastActivityAtDesc(
                        deviceHash, game.getId(), PlayStatus.ACTIVE);
        if (active.isPresent()) {
            active.get().touch();
            return active.get();
        }
        GameRelease release = publishing.currentRelease(game);
        PlaySession session = new PlaySession(deviceHash, release);
        initializeExplorationSession(session, publishing.readSnapshot(release));
        return sessions.save(session);
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
        ReleaseSnapshot snapshot = publishing.readSnapshot(session.getRelease());
        Set<String> solvedStages = stageKeys(session.getSolvedStagesJson());
        Set<String> discoveredStageKeys = discoveredStageKeys(snapshot, session);
        ReleaseSnapshot.StageSnapshot stage;
        if (flowMode(snapshot) == GameFlowMode.QR_EXPLORATION) {
            stage = snapshot.stages().stream()
                    .filter(candidate -> Objects.equals(candidate.stableKey(), session.getActiveStageKey()))
                    .filter(candidate -> discoveredStageKeys.contains(candidate.stableKey()))
                    .filter(candidate -> !solvedStages.contains(candidate.stableKey()))
                    .findFirst().orElse(null);
        } else {
            stage = session.getProgressIndex() < snapshot.stages().size()
                    ? snapshot.stages().get(session.getProgressIndex()) : null;
            for (int i = 0; i < Math.min(session.getProgressIndex(), snapshot.stages().size()); i++) {
                solvedStages.add(snapshot.stages().get(i).stableKey());
            }
        }
        List<ReleaseSnapshot.StageSnapshot> discoveredStages = snapshot.stages().stream()
                .filter(candidate -> discoveredStageKeys.contains(candidate.stableKey())).toList();
        return new PlayView(snapshot, session, stage, inventoryItems(snapshot, inventoryKeys(session)),
                scannedClues(snapshot, session), session.getNotes(), discoveredStages, Set.copyOf(solvedStages));
    }

    @Transactional
    public SolveResult solve(String slug, String deviceHash, String submittedAnswer) {
        EscapeGame game = games.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        PlaySession session = activeSession(deviceHash, game.getId());
        ReleaseSnapshot snapshot = publishing.readSnapshot(session.getRelease());
        if (flowMode(snapshot) == GameFlowMode.QR_EXPLORATION) {
            return solveExploration(session, snapshot, submittedAnswer);
        }
        if (session.getProgressIndex() >= snapshot.stages().size()) {
            if (!session.isCompleted()) session.complete();
            return new SolveResult(true, true, null);
        }
        ReleaseSnapshot.StageSnapshot stage = snapshot.stages().get(session.getProgressIndex());
        Set<String> inventory = inventoryKeys(session);
        if (stage.requiredItem() != null && !inventory.contains(stage.requiredItem())) {
            session.recordFailedAttempt();
            attempts.save(new PlayAttempt(session, stage.stableKey(), AttemptKind.SOLVE, false));
            return new SolveResult(false, false, "먼저 필요한 아이템을 찾아야 합니다.");
        }
        boolean success = !stage.puzzleType().requiresAnswer()
                || answers.matches(stage.puzzleType(), submittedAnswer, stage.answerDigest());
        attempts.save(new PlayAttempt(session, stage.stableKey(), AttemptKind.SOLVE, success));
        if (!success) {
            session.recordFailedAttempt();
            return new SolveResult(false, false, "자물쇠가 열리지 않았습니다. 단서를 다시 살펴보세요.");
        }
        if (stage.rewardItem() != null) {
            inventory.add(stage.rewardItem());
            session.setInventoryJson(writeInventory(inventory));
        }
        session.advance();
        boolean completed = session.getProgressIndex() >= snapshot.stages().size();
        if (completed) session.complete();
        return new SolveResult(true, completed, null);
    }

    @Transactional
    public String revealHint(String slug, String deviceHash) {
        EscapeGame game = games.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        PlaySession session = activeSession(deviceHash, game.getId());
        ReleaseSnapshot snapshot = publishing.readSnapshot(session.getRelease());
        ReleaseSnapshot.StageSnapshot stage;
        if (flowMode(snapshot) == GameFlowMode.QR_EXPLORATION) {
            stage = snapshot.stages().stream()
                    .filter(candidate -> Objects.equals(candidate.stableKey(), session.getActiveStageKey()))
                    .findFirst().orElse(null);
            if (stage == null) return "";
        } else {
            if (session.getProgressIndex() >= snapshot.stages().size()) return "";
            stage = snapshot.stages().get(session.getProgressIndex());
        }
        session.recordHint();
        attempts.save(new PlayAttempt(session, stage.stableKey(), AttemptKind.HINT, true));
        return stage.hint();
    }

    @Transactional
    public PlaySession restart(String slug, String deviceHash) {
        EscapeGame game = playableGame(slug);
        sessions.findFirstByDeviceTokenHashAndRelease_Game_IdAndStatusOrderByLastActivityAtDesc(
                deviceHash, game.getId(), PlayStatus.ACTIVE).ifPresent(PlaySession::abandon);
        GameRelease release = publishing.currentRelease(game);
        PlaySession session = new PlaySession(deviceHash, release);
        initializeExplorationSession(session, publishing.readSnapshot(release));
        return sessions.save(session);
    }

    @Transactional
    public void saveNotes(String slug, String deviceHash, String notes) {
        EscapeGame game = games.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        PlaySession session = activeSession(deviceHash, game.getId());
        ReleaseSnapshot snapshot = publishing.readSnapshot(session.getRelease());
        if (!snapshot.allowNotebook()) throw new IllegalArgumentException("이 게임에서는 노트를 사용할 수 없습니다.");
        String safeNotes = Objects.toString(notes, "");
        if (safeNotes.length() > 20_000) throw new IllegalArgumentException("노트는 20,000자 이하로 저장해 주세요.");
        session.setNotes(safeNotes.isBlank() ? null : safeNotes);
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
        PlaySession session = activeSession(deviceHash, game.getId());
        ReleaseSnapshot snapshot = publishing.readSnapshot(session.getRelease());
        if (requireScannerEnabled && !snapshot.allowQrScanner()) {
            return new QrScanResult(false, false, false, "이 게임에서는 인앱 QR 스캐너를 사용하지 않습니다.", "STAGE");
        }
        if (flowMode(snapshot) != GameFlowMode.QR_EXPLORATION) {
            return new QrScanResult(true, false, false, "이 게임은 정해진 순서대로 진행됩니다.", "STAGE");
        }
        ReleaseSnapshot.StageSnapshot stage = snapshot.stages().stream()
                .filter(candidate -> candidate.stableKey().equals(stageStableKey)).findFirst().orElse(null);
        if (stage == null) return new QrScanResult(false, false, false, "현재 게임의 문제가 아닙니다.", "STAGE");
        if (entryMode(stage) != StageEntryMode.QR) {
            return new QrScanResult(true, false, false, "QR로 공개되는 문제가 아닙니다.", "STAGE");
        }
        Set<String> solved = stageKeys(session.getSolvedStagesJson());
        if (solved.contains(stageStableKey)) {
            return new QrScanResult(true, true, true, "이미 해결한 문제입니다.", "STAGE");
        }
        LinkedHashSet<String> discovered = stageKeys(session.getDiscoveredStagesJson());
        boolean isNew = discovered.add(stageStableKey);
        session.setDiscoveredStagesJson(writeKeys(discovered));
        session.setActiveStageKey(stageStableKey);
        return new QrScanResult(true, true, true,
                isNew ? "새 문제를 발견했습니다." : "발견한 문제를 다시 엽니다.", "STAGE");
    }

    @Transactional
    public boolean selectDiscoveredStage(String slug, String deviceHash, String stageStableKey) {
        EscapeGame game = games.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        PlaySession session = activeSession(deviceHash, game.getId());
        ReleaseSnapshot snapshot = publishing.readSnapshot(session.getRelease());
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

    private SolveResult solveExploration(PlaySession session, ReleaseSnapshot snapshot, String submittedAnswer) {
        ReleaseSnapshot.StageSnapshot stage = snapshot.stages().stream()
                .filter(candidate -> Objects.equals(candidate.stableKey(), session.getActiveStageKey()))
                .findFirst().orElse(null);
        if (stage == null) return new SolveResult(false, false, "먼저 현장의 문제 QR을 찾아 주세요.");
        Set<String> discovered = discoveredStageKeys(snapshot, session);
        if (!discovered.contains(stage.stableKey())) {
            return new SolveResult(false, false, "아직 발견하지 않은 문제입니다.");
        }
        Set<String> solved = stageKeys(session.getSolvedStagesJson());
        if (solved.contains(stage.stableKey())) {
            session.setActiveStageKey(null);
            return new SolveResult(true, solved.size() >= snapshot.stages().size(), null);
        }
        Set<String> inventory = inventoryKeys(session);
        if (stage.requiredItem() != null && !inventory.contains(stage.requiredItem())) {
            session.recordFailedAttempt();
            attempts.save(new PlayAttempt(session, stage.stableKey(), AttemptKind.SOLVE, false));
            return new SolveResult(false, false, "먼저 필요한 단서를 찾아야 합니다.");
        }
        boolean success = !stage.puzzleType().requiresAnswer()
                || answers.matches(stage.puzzleType(), submittedAnswer, stage.answerDigest());
        attempts.save(new PlayAttempt(session, stage.stableKey(), AttemptKind.SOLVE, success));
        if (!success) {
            session.recordFailedAttempt();
            return new SolveResult(false, false, "자물쇠가 열리지 않았습니다. 단서를 다시 살펴보세요.");
        }
        if (stage.rewardItem() != null) {
            inventory.add(stage.rewardItem());
            session.setInventoryJson(writeInventory(inventory));
        }
        session.recordSuccessfulAttempt();
        solved.add(stage.stableKey());
        session.setSolvedStagesJson(writeKeys(solved));
        ReleaseSnapshot.StageSnapshot nextStage = snapshot.stages().stream()
                .filter(candidate -> Objects.equals(candidate.stableKey(), stage.nextStageKey()))
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
        if (completed) session.complete();
        String message = completed ? "모든 이야기를 완료했습니다."
                : nextStage != null ? "다음 장면이 열렸습니다."
                : stage.puzzleType() == PuzzleType.STORY ? "이야기를 확인했습니다."
                : "자물쇠가 열렸습니다!";
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
        PlaySession session = activeSession(deviceHash, game.getId());
        ReleaseSnapshot snapshot = publishing.readSnapshot(session.getRelease());
        if (requireScannerEnabled && !snapshot.allowQrScanner()) {
            return new ClueScanResult(false, false, false, "이 게임에서는 인앱 QR 스캐너를 사용하지 않습니다.", null);
        }
        ReleaseSnapshot.ItemSnapshot item = snapshot.items().stream()
                .filter(candidate -> candidate.stableKey().equals(itemStableKey)).findFirst().orElse(null);
        if (item == null) return new ClueScanResult(false, false, false, "현재 게임의 단서가 아닙니다.", null);
        if (!item.qrEnabled()) return new ClueScanResult(true, false, false, "QR로 공개된 단서가 아닙니다.", null);
        boolean isNew = scannedClueRepository.findByPlaySessionIdAndItemStableKey(session.getId(), itemStableKey).isEmpty();
        if (isNew) scannedClueRepository.save(new ScannedClue(session, itemStableKey));
        LinkedHashSet<String> inventory = inventoryKeys(session);
        if (inventory.add(itemStableKey)) session.setInventoryJson(writeInventory(inventory));
        session.touch();
        return new ClueScanResult(true, true, true,
                isNew ? "새 단서를 발견했습니다." : "이미 발견한 단서입니다.", item);
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

    private void initializeExplorationSession(PlaySession session, ReleaseSnapshot snapshot) {
        if (flowMode(snapshot) != GameFlowMode.QR_EXPLORATION) return;
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
            throw new IllegalStateException("플레이 진행을 저장하지 못했습니다.", e);
        }
    }

    private List<ReleaseSnapshot.ItemSnapshot> inventoryItems(ReleaseSnapshot snapshot, Set<String> keys) {
        return snapshot.items().stream().filter(item -> keys.contains(item.stableKey())).toList();
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

    public record ScannedClueView(ReleaseSnapshot.ItemSnapshot item, java.time.Instant scannedAt) {
        public ReleaseSnapshot.ItemSnapshot getItem() { return item; }
        public java.time.Instant getScannedAt() { return scannedAt; }
        public String getStableKey() { return item.stableKey(); }
        public String getName() { return item.name(); }
        public String getDescription() { return item.description(); }
        public String getEmoji() { return item.emoji(); }
        public String getImageUrl() { return item.imageUrl(); }
        public String getClueText() { return item.clueText(); }
    }

    public record ClueScanResult(boolean found, boolean accepted, boolean success,
                                 String message, ReleaseSnapshot.ItemSnapshot item) {
        public boolean isFound() { return found; }
        public boolean isAccepted() { return accepted; }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public ReleaseSnapshot.ItemSnapshot getItem() { return item; }
    }

    public record QrScanResult(boolean found, boolean accepted, boolean success,
                               String message, String targetType) {
        public static QrScanResult clue(ClueScanResult result) {
            return new QrScanResult(result.found(), result.accepted(), result.success(), result.message(), "CLUE");
        }
    }

    public record SolveResult(boolean success, boolean completed, String message) {
        public boolean isSuccess() { return success; }
        public boolean isCompleted() { return completed; }
        public String getMessage() { return message; }
    }

    public record PlaySummary(String title, int attemptCount, int hintsUsed, long elapsedMinutes,
                              java.time.Instant startedAt, java.time.Instant completedAt) {
        public String getTitle() { return title; }
        public int getAttemptCount() { return attemptCount; }
        public int getHintsUsed() { return hintsUsed; }
        public long getElapsedMinutes() { return elapsedMinutes; }
        public java.time.Instant getStartedAt() { return startedAt; }
        public java.time.Instant getCompletedAt() { return completedAt; }
        public int getHintCount() { return hintsUsed; }
        public String getElapsedDisplay() {
            long hours = elapsedMinutes / 60;
            long minutes = elapsedMinutes % 60;
            return hours > 0 ? String.format("%d시간 %02d분", hours, minutes) : minutes + "분";
        }
    }
}
