package com.findguni.controller;

import com.findguni.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@Controller
@RequestMapping("/play/{slug}")
public class PlayController {
    private final PlayService plays;
    private final AnonymousDeviceService devices;
    private final QRCodeService qrCodes;

    public PlayController(PlayService plays, AnonymousDeviceService devices,
                          QRCodeService qrCodes) {
        this.plays = plays;
        this.devices = devices;
        this.qrCodes = qrCodes;
    }

    @GetMapping
    public String landing(@PathVariable String slug, HttpServletRequest request, Model model) {
        String rawToken = devices.token(request).orElse(null);
        String deviceHash = rawToken == null ? null : devices.hash(rawToken);
        boolean hasActive = deviceHash != null && plays.hasActiveSession(slug, deviceHash);
        PlayService.PlayView activeView = hasActive ? plays.current(slug, deviceHash) : null;
        ReleaseSnapshot snapshot = hasActive ? activeView.game() : plays.publishedSnapshot(slug);
        model.addAttribute("game", snapshot);
        model.addAttribute("hasActiveSession", hasActive);
        if (hasActive) {
            int count = activeView.game().stages().size();
            int index = flowMode(activeView.game()) == com.findguni.model.GameFlowMode.QR_EXPLORATION
                    ? activeView.getSolvedStageCount() : activeView.session().getProgressIndex();
            model.addAttribute("stageNumber", Math.min(index + 1, Math.max(1, count)));
            model.addAttribute("currentStageNumber", Math.min(index + 1, Math.max(1, count)));
            model.addAttribute("stageCount", count);
            model.addAttribute("progressPercent", count == 0 ? 100 : Math.round(index * 100.0 / count));
        }
        return "player/landing";
    }

    @PostMapping("/start")
    public String start(@PathVariable String slug, HttpServletRequest request, HttpServletResponse response) {
        String rawToken = devices.ensureToken(request, response);
        plays.startOrResume(slug, devices.hash(rawToken));
        return "redirect:/play/" + slug + "/stage";
    }

    @GetMapping("/start")
    public String startFromQr(@PathVariable String slug, HttpServletRequest request, HttpServletResponse response) {
        String rawToken = devices.ensureToken(request, response);
        plays.startOrResume(slug, devices.hash(rawToken));
        return "redirect:/play/" + slug + "/stage";
    }

    @GetMapping("/stage")
    public String stage(@PathVariable String slug, HttpServletRequest request, Model model) {
        String rawToken = devices.token(request).orElse(null);
        if (rawToken == null) return "redirect:/play/" + slug;
        PlayService.PlayView view;
        try {
            view = plays.current(slug, devices.hash(rawToken));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return "redirect:/play/" + slug;
        }
        if (view.session().isCompleted() || view.stage() == null) {
            if (!view.session().isCompleted()
                    && flowMode(view.game()) == com.findguni.model.GameFlowMode.QR_EXPLORATION) {
                addPlayTools(model, view);
                int count = view.game().stages().size();
                model.addAttribute("stageCount", count);
                model.addAttribute("progressPercent", count == 0 ? 0
                        : Math.round(view.getSolvedStageCount() * 100.0 / count));
                model.addAttribute("hasActiveSession", true);
                return "player/hunt";
            }
            model.addAttribute("playSummary", plays.summary(slug, devices.hash(rawToken)));
            model.addAttribute("game", view.game());
            model.addAttribute("slug", slug);
            return "player/complete";
        }
        int count = view.game().stages().size();
        int index = flowMode(view.game()) == com.findguni.model.GameFlowMode.QR_EXPLORATION
                ? view.getSolvedStageCount() : view.session().getProgressIndex();
        model.addAttribute("game", view.game());
        model.addAttribute("stage", view.stage());
        addPlayTools(model, view);
        model.addAttribute("stageNumber", index + 1);
        model.addAttribute("stageCount", count);
        model.addAttribute("progressPercent", count == 0 ? 100 : Math.round(index * 100.0 / count));
        model.addAttribute("hasActiveSession", true);
        PlayService.HintAvailability hintAvailability = plays.hintAvailability(view);
        model.addAttribute("hintAvailability", hintAvailability);
        if (!model.containsAttribute("hintRevealed")) {
            model.addAttribute("hintRevealed", hintAvailability.alreadyRevealed());
            if (hintAvailability.alreadyRevealed()) model.addAttribute("revealedHint", view.stage().hint());
        }
        return "player/stage";
    }

    @GetMapping("/stage/{stableKey}")
    public String selectStage(@PathVariable String slug, @PathVariable String stableKey,
                              HttpServletRequest request, RedirectAttributes redirect) {
        String rawToken = devices.token(request).orElse(null);
        if (rawToken == null) return "redirect:/play/" + slug;
        if (!plays.selectDiscoveredStage(slug, devices.hash(rawToken), stableKey)) {
            redirect.addFlashAttribute("error", "Unable to select that stage.");
        }
        return "redirect:/play/" + slug + "/stage";
    }

    @PostMapping("/solve")
    public String solve(@PathVariable String slug, HttpServletRequest request,
                        @RequestParam Map<String, String> fields, RedirectAttributes redirect) {
        String rawToken = devices.token(request).orElse(null);
        if (rawToken == null) return "redirect:/play/" + slug;
        String answer = firstAnswer(fields);
        PlayService.SolveResult result = plays.solve(slug, devices.hash(rawToken), answer);
        if (!result.success()) redirect.addFlashAttribute("error", result.message());
        else redirect.addFlashAttribute("success",
                result.message() == null || result.message().isBlank() ? "The answer is accepted." : result.message());
        return "redirect:/play/" + slug + "/stage";
    }

    @PostMapping("/hint")
    public String hint(@PathVariable String slug, HttpServletRequest request, RedirectAttributes redirect) {
        String rawToken = devices.token(request).orElse(null);
        if (rawToken == null) return "redirect:/play/" + slug;
        PlayService.HintRevealResult result = plays.revealHint(slug, devices.hash(rawToken));
        if (result.revealed()) {
            redirect.addFlashAttribute("hintRevealed", true);
            redirect.addFlashAttribute("revealedHint", result.hint());
        } else {
            redirect.addFlashAttribute("error", result.message());
        }
        return "redirect:/play/" + slug + "/stage";
    }

    @PostMapping("/restart")
    public String restart(@PathVariable String slug, HttpServletRequest request, HttpServletResponse response) {
        String rawToken = devices.ensureToken(request, response);
        plays.restart(slug, devices.hash(rawToken));
        return "redirect:/play/" + slug + "/stage";
    }

    @PostMapping("/notes")
    public String notes(@PathVariable String slug, @RequestParam(defaultValue = "") String notes,
                        HttpServletRequest request, RedirectAttributes redirect) {
        String rawToken = devices.token(request).orElse(null);
        if (rawToken == null) return "redirect:/play/" + slug;
        try {
            plays.saveNotes(slug, devices.hash(rawToken), notes);
            redirect.addFlashAttribute("success", "Notes saved.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/play/" + slug + "/stage";
    }

    @PostMapping("/scan")
    @ResponseBody
    public ResponseEntity<?> scan(
            @PathVariable String slug,
            @RequestParam(name = "payload", required = false) String payload,
            @RequestParam(name = "frame", required = false) MultipartFile frame,
            HttpServletRequest request) {
        String rawToken = devices.token(request).orElse(null);
        if (rawToken == null) {
            return ResponseEntity.status(409).body(new PlayService.QrScanResult(
                    false, false, false, "No player session found. Start the game first.", null, null, null));
        }
        try {
            String detected = payload;
            if ((detected == null || detected.isBlank()) && frame != null && !frame.isEmpty()) {
                detected = qrCodes.decode(frame).orElse(null);
            }
            QRCodeService.QrTarget target = qrCodes.parseTarget(detected, slug).orElse(null);
            if (target == null) {
                return ResponseEntity.ok(new PlayService.QrScanResult(
                        false, false, false, "Invalid QR code for this game.", "STAGE", null, null));
            }
            if (target.type() == QRCodeService.QrTargetType.STAGE) {
                PlayService.QrScanResult result = plays.scanStage(slug, devices.hash(rawToken), target.stableKey(), true);
                String redirectUrl = "/play/" + slug + "/puzzle/" + target.stableKey();
                return ResponseEntity.ok(new PlayService.QrScanResult(
                        result.found(), result.accepted(), result.success(), result.message(), "STAGE", redirectUrl, null));
            }
            PlayService.ClueScanResult clue = plays.scanClue(slug, devices.hash(rawToken), target.stableKey());
            String redirectUrl = "/play/" + slug + "/clue/" + target.stableKey();
            return ResponseEntity.ok(new PlayService.QrScanResult(
                    clue.found(), clue.accepted(), clue.success(), clue.message(), "CLUE", redirectUrl,
                    clue.getItem() == null ? null : clue.getItem().stableKey()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new PlayService.QrScanResult(
                    false, false, false, e.getMessage(), null, null, null));
        }
    }
    @GetMapping("/clue/{stableKey}")
    public String clue(@PathVariable String slug, @PathVariable String stableKey,
                       HttpServletRequest request, HttpServletResponse response,
                       RedirectAttributes redirect) {
        String rawToken = devices.ensureToken(request, response);
        String hash = devices.hash(rawToken);
        if (!plays.hasActiveSession(slug, hash)) plays.startOrResume(slug, hash);
        PlayService.ClueScanResult result = plays.scanClueFromLink(slug, hash, stableKey);
        if (result.accepted()) redirect.addFlashAttribute("success", result.message());
        else redirect.addFlashAttribute("error", result.message());
        return "redirect:/play/" + slug + "/stage";
    }

    @GetMapping("/puzzle/{stableKey}")
    public String puzzle(@PathVariable String slug, @PathVariable String stableKey,
                         HttpServletRequest request, HttpServletResponse response,
                         RedirectAttributes redirect) {
        String rawToken = devices.ensureToken(request, response);
        String hash = devices.hash(rawToken);
        if (!plays.hasActiveSession(slug, hash)) plays.startOrResume(slug, hash);
        PlayService.QrScanResult result = plays.scanStage(slug, hash, stableKey, false);
        if (result.accepted()) redirect.addFlashAttribute("success", result.message());
        else redirect.addFlashAttribute("error", result.message());
        return "redirect:/play/" + slug + "/stage";
    }

    private void addPlayTools(Model model, PlayService.PlayView view) {
        model.addAttribute("game", view.game());
        model.addAttribute("inventory", view.inventory());
        model.addAttribute("requiredItems", plays.requiredItemViews(view));
        model.addAttribute("attemptCount", view.session().getAttemptCount());
        model.addAttribute("notes", view.notes());
        model.addAttribute("scannedClues", view.scannedClues());
        model.addAttribute("discoveredStages", view.discoveredStages());
        model.addAttribute("solvedStageKeys", view.solvedStageKeys());
        model.addAttribute("allowNotebook", view.game().allowNotebook());
        model.addAttribute("allowCluebook", view.game().allowCluebook());
        model.addAttribute("allowQrScanner", view.game().allowQrScanner());
    }

    private com.findguni.model.GameFlowMode flowMode(ReleaseSnapshot snapshot) {
        return snapshot.flowMode() == null ? com.findguni.model.GameFlowMode.LINEAR : snapshot.flowMode();
    }

    private String firstAnswer(Map<String, String> fields) {
        for (String key : new String[]{"answer", "code", "value", "selectedOption", "sequence", "direction", "colors"}) {
            String value = fields.get(key);
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }
}

