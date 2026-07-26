package com.findguni.controller;

import com.findguni.model.*;
import com.findguni.service.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/maker")
public class MakerController {
    private final AccountService accounts;
    private final GameAuthoringService authoring;
    private final PublishingService publishing;
    private final QRCodeService qrCodes;
    private final QrPrintKitService qrPrintKits;
    private final AssetStorageService assets;
    private final AudioStorageService audioStorage;
    private final OpenverseAudioService openverseAudio;
    private final PlayService plays;
    private final AnonymousDeviceService devices;

    public MakerController(AccountService accounts, GameAuthoringService authoring,
                           PublishingService publishing, QRCodeService qrCodes, AssetStorageService assets,
                           QrPrintKitService qrPrintKits, AudioStorageService audioStorage,
                           OpenverseAudioService openverseAudio, PlayService plays,
                           AnonymousDeviceService devices) {
        this.accounts = accounts;
        this.authoring = authoring;
        this.publishing = publishing;
        this.qrCodes = qrCodes;
        this.qrPrintKits = qrPrintKits;
        this.assets = assets;
        this.audioStorage = audioStorage;
        this.openverseAudio = openverseAudio;
        this.plays = plays;
        this.devices = devices;
    }

    @GetMapping
    public String dashboard(Authentication authentication, Model model) {
        UserAccount maker = accounts.current(authentication);
        model.addAttribute("maker", maker);
        model.addAttribute("games", authoring.ownedGames(maker));
        model.addAttribute("stats", authoring.makerStats(maker));
        return "maker/dashboard";
    }

    @GetMapping("/games/new")
    public String newGame(Model model) {
        model.addAttribute("game", new EscapeGame());
        addGameEnums(model);
        model.addAttribute("templates", templateOptions());
        return "maker/game-new";
    }

    @PostMapping("/games/new")
    public String createGame(Authentication authentication,
                             @RequestParam String title,
                             @RequestParam(defaultValue = "") String slug,
                             @RequestParam(defaultValue = "") String summary,
                             @RequestParam(defaultValue = "") String intro,
                             @RequestParam(defaultValue = "") String coverImageUrl,
                             @RequestParam(defaultValue = "#8B5CF6") String accentColor,
                             @RequestParam(defaultValue = "#EC4899") String secondaryColor,
                             @RequestParam(defaultValue = "#0B1020") String backgroundColor,
                             @RequestParam(defaultValue = "🔐") String gameIcon,
                              @RequestParam(name = "allowNotebook", required = false) List<String> allowNotebookValues,
                              @RequestParam(name = "allowCluebook", required = false) List<String> allowCluebookValues,
                              @RequestParam(name = "allowQrScanner", required = false) List<String> allowQrScannerValues,
                              @RequestParam(name = "unlimitedHints", required = false) List<String> unlimitedHintsValues,
                              @RequestParam(name = "hintLimit", required = false) Integer hintLimit,
                              @RequestParam(name = "hintCooldownSeconds", required = false) Integer hintCooldownSeconds,
                             @RequestParam(name = "bgmFile", required = false) MultipartFile bgmFile,
                             @RequestParam(name = "bgmUrl", required = false) String bgmUrl,
                             @RequestParam(name = "bgmTitle", required = false) String bgmTitle,
                             @RequestParam(name = "bgmCreator", required = false) String bgmCreator,
                             @RequestParam(name = "bgmLicense", required = false) String bgmLicense,
                             @RequestParam(name = "bgmLicenseUrl", required = false) String bgmLicenseUrl,
                             @RequestParam(name = "bgmSourceUrl", required = false) String bgmSourceUrl,
                             @RequestParam(name = "bgmVolume", required = false) Double bgmVolume,
                             @RequestParam(name = "bgmLoop", required = false) List<String> bgmLoopValues,
                             @RequestParam(name = "storyTextSpeed", required = false) Integer storyTextSpeed,
                             @RequestParam(name = "enableVignette", required = false) List<String> enableVignetteValues,
                             @RequestParam(name = "removeBgm", required = false) List<String> removeBgmValues,
                              @RequestParam(defaultValue = "MYSTERY_MANSION") String template,
                              @RequestParam(defaultValue = "QR_EXPLORATION") GameFlowMode flowMode,
                              @RequestParam(defaultValue = "MIDNIGHT") GameTheme theme,
                             @RequestParam(defaultValue = "NORMAL") Difficulty difficulty,
                             @RequestParam(defaultValue = "30") int estimatedMinutes,
                             @RequestParam(defaultValue = "LINK_ONLY") GameVisibility visibility,
                             Model model, RedirectAttributes redirect) {
        boolean allowNotebook = anyTrue(allowNotebookValues, true);
        boolean allowCluebook = anyTrue(allowCluebookValues, true);
        boolean allowQrScanner = anyTrue(allowQrScannerValues, true);
        boolean unlimitedHints = anyTrue(unlimitedHintsValues, true);
        boolean removeBgm = anyTrue(removeBgmValues, false);
        try {
            UserAccount maker = accounts.current(authentication);
            EscapeGame game = authoring.create(maker, title, slug, template,
                    theme, difficulty, estimatedMinutes, flowMode);
            authoring.ownedGame(game.getId(), maker);
            String uploadedBgm = removeBgm ? null : audioStorage.storeBgm(bgmFile);
            String selectedBgm = resolveMediaUrl(null, bgmUrl, uploadedBgm, removeBgm);
            authoring.updateSettings(game.getId(), maker, title, game.getSlug(), summary,
                    intro, coverImageUrl, accentColor, secondaryColor, backgroundColor, gameIcon,
                    allowNotebook, allowCluebook, allowQrScanner, theme, difficulty, estimatedMinutes, visibility,
                    selectedBgm, removeBgm ? null : bgmTitle, removeBgm ? null : bgmCreator,
                    removeBgm ? null : bgmLicense, removeBgm ? null : bgmLicenseUrl,
                     removeBgm ? null : bgmSourceUrl, bgmVolume == null ? 0.55 : bgmVolume,
                     anyTrue(bgmLoopValues, true), storyTextSpeed == null ? 32 : storyTextSpeed,
                     anyTrue(enableVignetteValues, true), unlimitedHints,
                     hintLimit == null ? 3 : hintLimit,
                     hintCooldownSeconds == null ? 0 : hintCooldownSeconds);
            redirect.addFlashAttribute("success", "새 방탈출 초안을 만들었습니다.");
            return "redirect:/maker/games/" + game.getId() + "/edit";
        } catch (IllegalArgumentException e) {
            EscapeGame form = new EscapeGame();
            form.setTitle(title); form.setSlug(slug); form.setTheme(theme);
            form.setDifficulty(difficulty); form.setEstimatedMinutes(estimatedMinutes);
            form.setSummary(summary); form.setIntro(intro); form.setCoverImageUrl(coverImageUrl);
            form.setAccentColor(accentColor); form.setVisibility(visibility);
            form.setSecondaryColor(secondaryColor); form.setBackgroundColor(backgroundColor);
            form.setGameIcon(gameIcon); form.setAllowNotebook(allowNotebook);
            form.setAllowCluebook(allowCluebook); form.setAllowQrScanner(allowQrScanner);
            form.setUnlimitedHints(unlimitedHints); form.setHintLimit(hintLimit == null ? 3 : hintLimit);
            form.setHintCooldownSeconds(hintCooldownSeconds == null ? 0 : hintCooldownSeconds);
            form.setFlowMode(flowMode);
            form.setBgmUrl(removeBgm ? null : bgmUrl); form.setBgmTitle(removeBgm ? null : bgmTitle);
            form.setBgmCreator(removeBgm ? null : bgmCreator); form.setBgmLicense(removeBgm ? null : bgmLicense);
            form.setBgmLicenseUrl(removeBgm ? null : bgmLicenseUrl);
            form.setBgmSourceUrl(removeBgm ? null : bgmSourceUrl);
            form.setBgmVolume(bgmVolume == null ? 0.55 : bgmVolume);
            form.setBgmLoop(anyTrue(bgmLoopValues, true));
            form.setStoryTextSpeed(storyTextSpeed == null ? 32 : storyTextSpeed);
            form.setEnableVignette(anyTrue(enableVignetteValues, true));
            model.addAttribute("game", form);
            model.addAttribute("error", e.getMessage());
            addGameEnums(model);
            model.addAttribute("templates", templateOptions());
            return "maker/game-new";
        } catch (IllegalStateException e) {
            model.addAttribute("game", new EscapeGame());
            model.addAttribute("error", e.getMessage());
            addGameEnums(model);
            model.addAttribute("templates", templateOptions());
            return "maker/game-new";
        }
    }

    @GetMapping("/games/{id}/edit")
    public String editGame(@PathVariable Long id,
                           @RequestParam(name = "edit", required = false) Long selectedStageId,
                           @RequestParam(name = "item", required = false) Long selectedItemId,
                           Authentication authentication, Model model) {
        UserAccount maker = accounts.current(authentication);
        EscapeGame game = authoring.ownedGame(id, maker);
        builderModel(model, game, maker);
        model.addAttribute("selectedStageId", selectedStageId);
        model.addAttribute("selectedItemId", selectedItemId);
        return "maker/game-builder";
    }

    @PostMapping("/games/{id}/edit")
    public String updateGame(@PathVariable Long id, Authentication authentication,
                             @RequestParam String title,
                             @RequestParam(defaultValue = "") String slug,
                             @RequestParam(defaultValue = "") String summary,
                             @RequestParam(defaultValue = "") String intro,
                             @RequestParam(defaultValue = "") String coverImageUrl,
                             @RequestParam(defaultValue = "#8B5CF6") String accentColor,
                             @RequestParam(defaultValue = "#EC4899") String secondaryColor,
                             @RequestParam(defaultValue = "#0B1020") String backgroundColor,
                             @RequestParam(defaultValue = "🔐") String gameIcon,
                              @RequestParam(name = "allowNotebook", required = false) List<String> allowNotebookValues,
                              @RequestParam(name = "allowCluebook", required = false) List<String> allowCluebookValues,
                              @RequestParam(name = "allowQrScanner", required = false) List<String> allowQrScannerValues,
                              @RequestParam(name = "unlimitedHints", required = false) List<String> unlimitedHintsValues,
                              @RequestParam(name = "hintLimit", required = false) Integer hintLimit,
                              @RequestParam(name = "hintCooldownSeconds", required = false) Integer hintCooldownSeconds,
                             @RequestParam(name = "bgmFile", required = false) MultipartFile bgmFile,
                             @RequestParam(name = "bgmUrl", required = false) String bgmUrl,
                             @RequestParam(name = "bgmTitle", required = false) String bgmTitle,
                             @RequestParam(name = "bgmCreator", required = false) String bgmCreator,
                             @RequestParam(name = "bgmLicense", required = false) String bgmLicense,
                             @RequestParam(name = "bgmLicenseUrl", required = false) String bgmLicenseUrl,
                             @RequestParam(name = "bgmSourceUrl", required = false) String bgmSourceUrl,
                             @RequestParam(name = "bgmVolume", required = false) Double bgmVolume,
                             @RequestParam(name = "bgmLoop", required = false) List<String> bgmLoopValues,
                             @RequestParam(name = "storyTextSpeed", required = false) Integer storyTextSpeed,
                             @RequestParam(name = "enableVignette", required = false) List<String> enableVignetteValues,
                             @RequestParam(name = "removeBgm", required = false) List<String> removeBgmValues,
                             @RequestParam(required = false) GameFlowMode flowMode,
                             @RequestParam(defaultValue = "MIDNIGHT") GameTheme theme,
                             @RequestParam(defaultValue = "NORMAL") Difficulty difficulty,
                             @RequestParam(defaultValue = "30") int estimatedMinutes,
                             @RequestParam(defaultValue = "LINK_ONLY") GameVisibility visibility,
                             RedirectAttributes redirect) {
        boolean allowNotebook = anyTrue(allowNotebookValues, false);
        boolean allowCluebook = anyTrue(allowCluebookValues, false);
        boolean allowQrScanner = anyTrue(allowQrScannerValues, false);
        try {
            UserAccount maker = accounts.current(authentication);
            EscapeGame existing = authoring.ownedGame(id, maker);
            boolean removeBgm = anyTrue(removeBgmValues, false);
            boolean hasBgmFile = !removeBgm && bgmFile != null && !bgmFile.isEmpty();
            String uploadedBgm = hasBgmFile ? audioStorage.storeBgm(bgmFile) : null;
            boolean bgmSelectionProvided = bgmUrl != null;
            boolean bgmChanged = removeBgm || hasBgmFile || bgmSelectionProvided;
            String selectedBgm = resolveMediaUrl(existing.getBgmUrl(), bgmUrl, uploadedBgm, removeBgm);
            authoring.updateSettings(id, maker, title, slug, summary, intro,
                    coverImageUrl, accentColor, secondaryColor, backgroundColor, gameIcon,
                    allowNotebook, allowCluebook, allowQrScanner, theme, difficulty, estimatedMinutes, visibility,
                    selectedBgm,
                    resolveMetadata(existing.getBgmTitle(), bgmTitle, bgmChanged, removeBgm),
                    resolveMetadata(existing.getBgmCreator(), bgmCreator, bgmChanged, removeBgm),
                    resolveMetadata(existing.getBgmLicense(), bgmLicense, bgmChanged, removeBgm),
                    resolveMetadata(existing.getBgmLicenseUrl(), bgmLicenseUrl, bgmChanged, removeBgm),
                    resolveMetadata(existing.getBgmSourceUrl(), bgmSourceUrl, bgmChanged, removeBgm),
                     bgmVolume == null ? existing.getBgmVolume() : bgmVolume,
                     anyTrueOrExisting(bgmLoopValues, existing.isBgmLoop()),
                     storyTextSpeed == null ? existing.getStoryTextSpeed() : storyTextSpeed,
                     anyTrueOrExisting(enableVignetteValues, existing.isEnableVignette()),
                     anyTrueOrExisting(unlimitedHintsValues, existing.isUnlimitedHints()),
                     hintLimit == null ? existing.getHintLimit() : hintLimit,
                     hintCooldownSeconds == null ? existing.getHintCooldownSeconds() : hintCooldownSeconds);
            if (flowMode != null) authoring.updateFlowMode(id, maker, flowMode);
            redirect.addFlashAttribute("success", "게임 설정을 저장했습니다.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/maker/games/" + id + "/edit";
    }

    @PostMapping("/games/{id}/stages")
    public String addStage(@PathVariable Long id, Authentication authentication,
                           @RequestParam String title,
                           @RequestParam(defaultValue = "") String story,
                           @RequestParam(defaultValue = "") String instruction,
                           @RequestParam(defaultValue = "") String hint,
                           @RequestParam(defaultValue = "STORY") PuzzleType puzzleType,
                           @RequestParam(defaultValue = "") String draftAnswer,
                           @RequestParam(defaultValue = "") String optionsText,
                           @RequestParam(defaultValue = "{}") String optionRoutesJson,
                           @RequestParam(defaultValue = "4") int lockLength,
                           @RequestParam(defaultValue = "") String requiredItemId,
                           @RequestParam(name = "requiredItemIds", required = false) List<String> requiredItemIds,
                           @RequestParam(name = "consumeRequiredItems", required = false) List<String> consumeRequiredItemValues,
                           @RequestParam(defaultValue = "") String rewardItemId,
                           @RequestParam(name = "qrEnabled", required = false) List<String> qrEnabledValues,
                           @RequestParam(name = "entryMode", defaultValue = "QR") StageEntryMode entryMode,
                           @RequestParam(name = "nextStageKey", required = false) String nextStageKey,
                           @RequestParam(defaultValue = "FADE") StoryEffect storyEffect,
                           @RequestParam(name = "scenePhoto", required = false) MultipartFile scenePhoto,
                           @RequestParam(name = "sceneImageUrl", required = false) String sceneImageUrl,
                           @RequestParam(name = "sfxFile", required = false) MultipartFile sfxFile,
                           @RequestParam(name = "sfxUrl", required = false) String sfxUrl,
                           @RequestParam(name = "sfxTitle", required = false) String sfxTitle,
                           @RequestParam(name = "sfxCreator", required = false) String sfxCreator,
                           @RequestParam(name = "sfxLicense", required = false) String sfxLicense,
                           @RequestParam(name = "sfxLicenseUrl", required = false) String sfxLicenseUrl,
                           @RequestParam(name = "sfxSourceUrl", required = false) String sfxSourceUrl,
                           @RequestParam(name = "sfxVolume", required = false) Double sfxVolume,
                           @RequestParam(name = "removeSfx", required = false) List<String> removeSfxValues,
                           @RequestParam(name = "removeSceneImage", required = false) List<String> removeSceneImageValues,
                           RedirectAttributes redirect) {
        try {
            UserAccount maker = accounts.current(authentication);
            authoring.ownedGame(id, maker);
            boolean removeSfx = anyTrue(removeSfxValues, false);
            boolean removeSceneImage = anyTrue(removeSceneImageValues, false);
            String uploadedScene = removeSceneImage ? null : assets.storeItemImage(scenePhoto);
            String uploadedSfx = removeSfx ? null : audioStorage.storeSfx(sfxFile);
            GameStage created = authoring.addStage(id, maker, stageDraft(title, story, instruction, hint,
                    puzzleType, draftAnswer, optionsText, lockLength, requiredItemId, rewardItemId,
                    entryMode == StageEntryMode.QR && anyTrue(qrEnabledValues, true),
                    storyEffect, resolveMediaUrl(null, sceneImageUrl, uploadedScene, removeSceneImage),
                    resolveMediaUrl(null, sfxUrl, uploadedSfx, removeSfx),
                    removeSfx ? "" : sfxTitle, removeSfx ? "" : sfxCreator,
                    removeSfx ? "" : sfxLicense, removeSfx ? "" : sfxLicenseUrl,
                    removeSfx ? "" : sfxSourceUrl, sfxVolume == null ? 0.8 : sfxVolume,
                    requiredItemIds, anyTrue(consumeRequiredItemValues, false)),
                    entryMode, nextStageKey);
            authoring.updateStageOptionRoutes(id, created.getId(), maker, optionRoutesJson);
            redirect.addFlashAttribute("success", "스테이지를 추가했습니다.");
            return "redirect:/maker/games/" + id + "/edit?tab=stages&edit=" + created.getId();
        } catch (IllegalArgumentException | IllegalStateException e) { redirect.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/maker/games/" + id + "/edit?tab=stages";
    }

    @PostMapping({"/stages/{stageId}", "/games/{gameId}/stages/{stageId}"})
    public String updateStage(@PathVariable(required = false) Long gameId, @PathVariable Long stageId, Authentication authentication,
                              @RequestParam String title,
                              @RequestParam(defaultValue = "") String story,
                              @RequestParam(defaultValue = "") String instruction,
                              @RequestParam(defaultValue = "") String hint,
                              @RequestParam(defaultValue = "STORY") PuzzleType puzzleType,
                              @RequestParam(defaultValue = "") String draftAnswer,
                              @RequestParam(defaultValue = "") String optionsText,
                              @RequestParam(defaultValue = "{}") String optionRoutesJson,
                              @RequestParam(defaultValue = "4") int lockLength,
                               @RequestParam(defaultValue = "") String requiredItemId,
                               @RequestParam(name = "requiredItemIds", required = false) List<String> requiredItemIds,
                               @RequestParam(name = "consumeRequiredItems", required = false) List<String> consumeRequiredItemValues,
                               @RequestParam(defaultValue = "") String rewardItemId,
                               @RequestParam(name = "qrEnabled", required = false) List<String> qrEnabledValues,
                               @RequestParam(name = "entryMode", required = false) StageEntryMode entryMode,
                               @RequestParam(name = "nextStageKey", required = false) String nextStageKey,
                               @RequestParam(name = "storyEffect", required = false) StoryEffect storyEffect,
                              @RequestParam(name = "scenePhoto", required = false) MultipartFile scenePhoto,
                              @RequestParam(name = "sceneImageUrl", required = false) String sceneImageUrl,
                              @RequestParam(name = "sfxFile", required = false) MultipartFile sfxFile,
                              @RequestParam(name = "sfxUrl", required = false) String sfxUrl,
                              @RequestParam(name = "sfxTitle", required = false) String sfxTitle,
                              @RequestParam(name = "sfxCreator", required = false) String sfxCreator,
                              @RequestParam(name = "sfxLicense", required = false) String sfxLicense,
                              @RequestParam(name = "sfxLicenseUrl", required = false) String sfxLicenseUrl,
                              @RequestParam(name = "sfxSourceUrl", required = false) String sfxSourceUrl,
                              @RequestParam(name = "sfxVolume", required = false) Double sfxVolume,
                              @RequestParam(name = "removeSfx", required = false) List<String> removeSfxValues,
                              @RequestParam(name = "removeSceneImage", required = false) List<String> removeSceneImageValues,
                              @RequestParam(defaultValue = "false") boolean preview,
                              RedirectAttributes redirect) {
        UserAccount maker = accounts.current(authentication);
        GameStage existing = gameId == null ? authoring.ownedStage(stageId, maker)
                : authoring.ownedStage(gameId, stageId, maker);
        Long targetGameId = existing.getGame().getId();
        try {
            boolean removeSfx = anyTrue(removeSfxValues, false);
            boolean removeSceneImage = anyTrue(removeSceneImageValues, false);
            boolean hasSceneFile = !removeSceneImage && scenePhoto != null && !scenePhoto.isEmpty();
            boolean hasSfxFile = !removeSfx && sfxFile != null && !sfxFile.isEmpty();
            String uploadedScene = hasSceneFile ? assets.storeItemImage(scenePhoto) : null;
            String uploadedSfx = hasSfxFile ? audioStorage.storeSfx(sfxFile) : null;
            boolean sfxChanged = removeSfx || hasSfxFile || sfxUrl != null;
            GameAuthoringService.StageDraft draft = stageDraft(title, story, instruction, hint, puzzleType,
                    draftAnswer, optionsText, lockLength, requiredItemId, rewardItemId,
                    anyTrueOrExisting(qrEnabledValues, existing.isQrEnabled()),
                    storyEffect == null ? existing.getStoryEffect() : storyEffect,
                    resolveMediaUrl(existing.getSceneImageUrl(), sceneImageUrl, uploadedScene, removeSceneImage),
                    resolveMediaUrl(existing.getSfxUrl(), sfxUrl, uploadedSfx, removeSfx),
                    resolveMetadata(existing.getSfxTitle(), sfxTitle, sfxChanged, removeSfx),
                    resolveMetadata(existing.getSfxCreator(), sfxCreator, sfxChanged, removeSfx),
                    resolveMetadata(existing.getSfxLicense(), sfxLicense, sfxChanged, removeSfx),
                    resolveMetadata(existing.getSfxLicenseUrl(), sfxLicenseUrl, sfxChanged, removeSfx),
                    resolveMetadata(existing.getSfxSourceUrl(), sfxSourceUrl, sfxChanged, removeSfx),
                    sfxVolume == null ? existing.getSfxVolume() : sfxVolume,
                    requiredItemIds, anyTrueOrExisting(consumeRequiredItemValues, existing.isConsumeRequiredItems()));
            if (gameId == null) authoring.updateStage(stageId, maker, draft, entryMode, nextStageKey);
            else authoring.updateStage(gameId, stageId, maker, draft, entryMode, nextStageKey);
            authoring.updateStageOptionRoutes(targetGameId, stageId, maker, optionRoutesJson);
            redirect.addFlashAttribute("success", "스테이지를 저장했습니다.");
            if (preview) {
                return "redirect:/maker/games/" + targetGameId + "/stages/" + stageId + "/preview";
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/maker/games/" + targetGameId + "/edit?tab=stages&edit=" + stageId;
    }

    @GetMapping("/games/{gameId}/stages/{stageId}/preview")
    public String previewStage(@PathVariable Long gameId, @PathVariable Long stageId,
                               Authentication authentication, HttpServletRequest request,
                               HttpServletResponse response, RedirectAttributes redirect) {
        UserAccount maker = accounts.current(authentication);
        GameStage stage = authoring.ownedStage(gameId, stageId, maker);
        EscapeGame game = stage.getGame();
        try {
            String rawToken = devices.ensureToken(request, response);
            if (!plays.openStagePreview(game.getSlug(), devices.hash(rawToken), stage.getStableKey())) {
                redirect.addFlashAttribute("error", "미리볼 문제를 찾지 못했습니다.");
                return "redirect:/maker/games/" + gameId + "/edit?tab=stages&edit=" + stageId;
            }
            return "redirect:/play/" + game.getSlug() + "/stage";
        } catch (org.springframework.web.server.ResponseStatusException e) {
            redirect.addFlashAttribute("error", "게임을 공개한 뒤 문제 미리보기를 사용할 수 있습니다.");
            return "redirect:/maker/games/" + gameId + "/edit?tab=stages&edit=" + stageId;
        }
    }

    @PostMapping({"/stages/{stageId}/delete", "/games/{gameId}/stages/{stageId}/delete"})
    public String deleteStage(@PathVariable(required = false) Long gameId, @PathVariable Long stageId, Authentication authentication,
                              RedirectAttributes redirect) {
        UserAccount maker = accounts.current(authentication);
        Long targetGameId = gameId == null ? authoring.deleteStage(stageId, maker)
                : authoring.deleteStage(gameId, stageId, maker);
        redirect.addFlashAttribute("success", "스테이지를 삭제했습니다.");
        return "redirect:/maker/games/" + targetGameId + "/edit?tab=stages";
    }

    @PostMapping({"/stages/{stageId}/move", "/games/{gameId}/stages/{stageId}/move"})
    public String moveStage(@PathVariable(required = false) Long gameId, @PathVariable Long stageId, @RequestParam String direction,
                            Authentication authentication) {
        UserAccount maker = accounts.current(authentication);
        Long targetGameId = gameId == null ? authoring.moveStage(stageId, maker, direction)
                : authoring.moveStage(gameId, stageId, maker, direction);
        return "redirect:/maker/games/" + targetGameId + "/edit?tab=stages&edit=" + stageId;
    }

    @PostMapping("/games/{id}/items")
    public String addItem(@PathVariable Long id, Authentication authentication,
                           @RequestParam(name = "itemType", defaultValue = "") String itemType,
                           @RequestParam(name = "type", defaultValue = "") String legacyType,
                           @RequestParam String name,
                           @RequestParam(defaultValue = "") String description,
                           @RequestParam(defaultValue = "") String clueText,
                           @RequestParam(name = "icon", defaultValue = "🗝️") String icon,
                           @RequestParam(defaultValue = "false") boolean qrEnabled,
                           @RequestParam(defaultValue = "false") boolean initiallyOwned,
                           @RequestParam(defaultValue = "") String copyableText,
                           @RequestParam(defaultValue = "") String alternateRequiredItem,
                           @RequestParam(defaultValue = "") String alternateScanText,
                           @RequestParam(name = "photo", required = false) MultipartFile photo,
                           RedirectAttributes redirect) {
        try {
            UserAccount maker = accounts.current(authentication);
            authoring.ownedGame(id, maker);
            String imageUrl = assets.storeItemImage(photo);
            GameItem created = authoring.addItem(id, maker, resolveItemType(itemType, legacyType), name, description,
                    clueText, icon, qrEnabled, initiallyOwned, copyableText, imageUrl,
                    alternateRequiredItem, alternateScanText);
            redirect.addFlashAttribute("success", "아이템을 추가했습니다.");
            return "redirect:/maker/games/" + id + "/edit?tab=items&item=" + created.getId();
        } catch (IllegalArgumentException | IllegalStateException e) { redirect.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/maker/games/" + id + "/edit?tab=items";
    }

    @PostMapping("/games/{gameId}/items/{itemId}")
    public String updateItem(@PathVariable Long gameId, @PathVariable Long itemId,
                             Authentication authentication, @RequestParam String name,
                             @RequestParam(name = "itemType", defaultValue = "") String itemType,
                             @RequestParam(name = "type", defaultValue = "") String legacyType,
                             @RequestParam(defaultValue = "") String description,
                             @RequestParam(defaultValue = "") String clueText,
                             @RequestParam(name = "icon", defaultValue = "🗝️") String icon,
                             @RequestParam(defaultValue = "false") boolean qrEnabled,
                             @RequestParam(defaultValue = "false") boolean initiallyOwned,
                             @RequestParam(defaultValue = "") String copyableText,
                             @RequestParam(defaultValue = "") String alternateRequiredItem,
                             @RequestParam(defaultValue = "") String alternateScanText,
                             @RequestParam(name = "photo", required = false) MultipartFile photo,
                             RedirectAttributes redirect) {
        try {
            UserAccount maker = accounts.current(authentication);
            GameItem existing = authoring.ownedItem(gameId, itemId, maker);
            String imageUrl = assets.storeItemImage(photo);
            authoring.updateItem(gameId, itemId, maker,
                    resolveItemType(itemType, legacyType, existing.getItemType()), name,
                    description, clueText, icon, qrEnabled, initiallyOwned, copyableText, imageUrl,
                    alternateRequiredItem, alternateScanText);
            redirect.addFlashAttribute("success", "아이템을 저장했습니다.");
        } catch (IllegalArgumentException | IllegalStateException e) { redirect.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/maker/games/" + gameId + "/edit?tab=items&item=" + itemId;
    }

    @PostMapping({"/items/{itemId}/delete", "/games/{gameId}/items/{itemId}/delete"})
    public String deleteItem(@PathVariable(required = false) Long gameId, @PathVariable Long itemId, Authentication authentication,
                             RedirectAttributes redirect) {
        UserAccount maker = accounts.current(authentication);
        Long targetGameId = gameId == null ? authoring.deleteItem(itemId, maker)
                : authoring.deleteItem(gameId, itemId, maker);
        redirect.addFlashAttribute("success", "아이템을 삭제했습니다.");
        return "redirect:/maker/games/" + targetGameId + "/edit?tab=items";
    }

    @PostMapping("/games/{id}/publish")
    public String publish(@PathVariable Long id, Authentication authentication, RedirectAttributes redirect) {
        try {
            GameRelease release = publishing.publish(id, accounts.current(authentication));
            redirect.addFlashAttribute("success", "버전 " + release.getVersionNumber() + "을 발행했습니다.");
        } catch (IllegalArgumentException e) { redirect.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/maker/games/" + id + "/edit";
    }

    @PostMapping("/games/{id}/hide")
    public String hide(@PathVariable Long id, Authentication authentication, RedirectAttributes redirect) {
        authoring.hide(id, accounts.current(authentication));
        redirect.addFlashAttribute("success", "플레이 링크를 숨겼습니다.");
        return "redirect:/maker/games/" + id + "/edit";
    }

    @GetMapping(value = "/games/{id}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    public ResponseEntity<byte[]> qr(@PathVariable Long id, Authentication authentication) {
        EscapeGame game = authoring.ownedGame(id, accounts.current(authentication));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.IMAGE_PNG).body(qrCodes.generateFor(game));
    }

    @GetMapping("/games/{id}/qr-kit")
    public String qrKit(@PathVariable Long id, Authentication authentication, Model model) {
        UserAccount maker = accounts.current(authentication);
        EscapeGame game = authoring.ownedGame(id, maker);
        QrPrintKitService.QrKit kit = qrPrintKits.build(game,
                authoring.stages(id, maker), authoring.items(id, maker));
        model.addAttribute("game", game);
        model.addAttribute("kit", kit);
        model.addAttribute("qrCards", kit.cards());
        model.addAttribute("qrPages", kit.pages());
        return "maker/qr-kit";
    }

    @GetMapping(value = "/games/{id}/qr-kit/print.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @ResponseBody
    public ResponseEntity<byte[]> qrKitPdf(@PathVariable Long id, Authentication authentication) {
        QrPrintKitService.QrKit kit = ownedQrKit(id, authentication);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(kit.slug() + "-qr-kit.pdf", StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(qrPrintKits.pdf(kit));
    }

    @GetMapping(value = "/games/{id}/qr-kit/qr-images.zip", produces = "application/zip")
    @ResponseBody
    public ResponseEntity<byte[]> qrKitZip(@PathVariable Long id, Authentication authentication) {
        QrPrintKitService.QrKit kit = ownedQrKit(id, authentication);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(kit.slug() + "-qr-images.zip", StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(qrPrintKits.zip(kit));
    }

    @GetMapping(value = "/audio/search", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> searchAudio(@RequestParam(name = "q", required = false) String query,
                                         @RequestParam(name = "kind", required = false) String kind) {
        try {
            OpenverseAudioService.AudioSearchResponse result = openverseAudio.search(
                    query, OpenverseAudioService.AudioKind.parse(kind));
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (OpenverseAudioService.OpenverseUnavailableException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping(value = "/games/{gameId}/items/{itemId}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    public ResponseEntity<byte[]> itemQr(@PathVariable Long gameId, @PathVariable Long itemId,
                                         Authentication authentication) {
        UserAccount maker = accounts.current(authentication);
        EscapeGame game = authoring.ownedGame(gameId, maker);
        GameItem item = authoring.ownedItem(gameId, itemId, maker);
        if (!item.isQrEnabled()) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "QR 단서가 활성화되지 않은 아이템입니다.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.IMAGE_PNG).body(qrCodes.generateForItem(game, item));
    }

    @GetMapping(value = "/games/{gameId}/stages/{stageId}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    public ResponseEntity<byte[]> stageQr(@PathVariable Long gameId, @PathVariable Long stageId,
                                          Authentication authentication) {
        UserAccount maker = accounts.current(authentication);
        EscapeGame game = authoring.ownedGame(gameId, maker);
        GameStage stage = authoring.ownedStage(gameId, stageId, maker);
        if (game.getFlowMode() != GameFlowMode.QR_EXPLORATION || !stage.isQrEnabled()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "QR로 공개된 문제가 아닙니다.");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.IMAGE_PNG).body(qrCodes.generateForStage(game, stage));
    }

    private void builderModel(Model model, EscapeGame game, UserAccount maker) {
        model.addAttribute("game", game);
        model.addAttribute("stages", authoring.stages(game.getId(), maker));
        model.addAttribute("items", authoring.items(game.getId(), maker));
        model.addAttribute("stats", authoring.gameStats(game.getId(), maker));
        model.addAttribute("playUrl", qrCodes.playUrl(game));
        addGameEnums(model);
        model.addAttribute("puzzleTypes", PuzzleType.values());
        model.addAttribute("itemTypes", ItemType.values());
        model.addAttribute("storyEffects", StoryEffect.values());
        model.addAttribute("stageEntryModes", StageEntryMode.values());
    }

    private QrPrintKitService.QrKit ownedQrKit(Long gameId, Authentication authentication) {
        UserAccount maker = accounts.current(authentication);
        EscapeGame game = authoring.ownedGame(gameId, maker);
        return qrPrintKits.build(game, authoring.stages(gameId, maker), authoring.items(gameId, maker));
    }

    private void addGameEnums(Model model) {
        model.addAttribute("themes", GameTheme.values());
        model.addAttribute("difficulties", Difficulty.values());
        model.addAttribute("visibilities", GameVisibility.values());
        model.addAttribute("flowModes", GameFlowMode.values());
    }

    private Map<String, String> templateOptions() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("BLANK", "빈 게임"); options.put("QUICK_10", "10분 퀵게임");
        options.put("MYSTERY_MANSION", "미스터리 저택"); options.put("DETECTIVE_CASE", "탐정 사건");
        options.put("HORROR_HOSPITAL", "공포 병원"); options.put("TREASURE_HUNT", "보물 사냥");
        options.put("SCHOOL_MISSION", "학교 미션"); options.put("MUSEUM_TOUR", "박물관 투어");
        options.put("SCI_FI_LAB", "SF 연구소"); options.put("FANTASY_QUEST", "판타지 퀘스트");
        options.put("OUTDOOR_TRAIL", "야외 트레일"); options.put("FESTIVAL_EVENT", "축제 이벤트");
        options.put("KIDS_ADVENTURE", "어린이 모험"); options.put("TEAM_RACE", "팀 레이스");
        return options;
    }

    private GameAuthoringService.StageDraft stageDraft(String title, String story, String instruction,
            String hint, PuzzleType puzzleType, String draftAnswer, String optionsText,
            int lockLength, String requiredItem, String rewardItem) {
        return new GameAuthoringService.StageDraft(title, story, instruction, hint, puzzleType,
                draftAnswer, optionsText, lockLength, blankToNull(requiredItem), blankToNull(rewardItem));
    }

    private GameAuthoringService.StageDraft stageDraft(String title, String story, String instruction,
            String hint, PuzzleType puzzleType, String draftAnswer, String optionsText,
            int lockLength, String requiredItem, String rewardItem, boolean qrEnabled,
            StoryEffect storyEffect,
            String sceneImageUrl, String sfxUrl, String sfxTitle, String sfxCreator,
            String sfxLicense, String sfxLicenseUrl, String sfxSourceUrl, Double sfxVolume,
            List<String> requiredItems, boolean consumeRequiredItems) {
        return new GameAuthoringService.StageDraft(title, story, instruction, hint, puzzleType,
                draftAnswer, optionsText, lockLength, blankToNull(requiredItem), blankToNull(rewardItem),
                qrEnabled, storyEffect, sceneImageUrl, sfxUrl, sfxTitle, sfxCreator, sfxLicense,
                sfxLicenseUrl, sfxSourceUrl, sfxVolume, requiredItems, consumeRequiredItems);
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private ItemType resolveItemType(String primary, String legacy) {
        return resolveItemType(primary, legacy, ItemType.CUSTOM);
    }

    private ItemType resolveItemType(String primary, String legacy, ItemType fallback) {
        String raw = primary == null || primary.isBlank() ? legacy : primary;
        if (raw == null || raw.isBlank()) return fallback == null ? ItemType.CUSTOM : fallback;
        try { return ItemType.valueOf(raw.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("지원하지 않는 아이템 유형입니다."); }
    }

    private boolean anyTrue(List<String> values, boolean defaultWhenMissing) {
        if (values == null || values.isEmpty()) return defaultWhenMissing;
        return values.stream().anyMatch(value -> "true".equalsIgnoreCase(value == null ? "" : value.trim()));
    }

    private boolean anyTrueOrExisting(List<String> values, boolean existing) {
        return values == null || values.isEmpty() ? existing : anyTrue(values, false);
    }

    private String resolveMediaUrl(String existing, String submitted, String uploaded, boolean remove) {
        if (remove) return "";
        if (uploaded != null && !uploaded.isBlank()) return uploaded;
        if (submitted != null) return submitted.trim();
        return existing;
    }

    private String resolveMetadata(String existing, String submitted, boolean mediaChanged, boolean remove) {
        if (remove) return "";
        if (submitted != null) return submitted;
        return mediaChanged ? "" : existing;
    }
}
