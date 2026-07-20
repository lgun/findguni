package com.findguni.controller;

import com.findguni.model.UserAccount;
import com.findguni.service.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/platform")
public class PlatformController {
    private final AccountService accounts;
    private final GameAuthoringService games;
    private final PlatformService platform;

    public PlatformController(AccountService accounts, GameAuthoringService games, PlatformService platform) {
        this.accounts = accounts;
        this.games = games;
        this.platform = platform;
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("stats", platform.stats());
        model.addAttribute("makers", accounts.makers());
        model.addAttribute("games", games.allGames());
        model.addAttribute("recentActivity", platform.recentActivity());
        return "platform/dashboard";
    }

    @PostMapping("/accounts/{id}/toggle")
    public String toggleAccount(@PathVariable Long id, Authentication authentication,
                                RedirectAttributes redirect) {
        try {
            UserAccount admin = accounts.current(authentication);
            accounts.toggleStatus(id, admin.getId());
            redirect.addFlashAttribute("success", "계정 상태를 변경했습니다.");
        } catch (IllegalArgumentException e) { redirect.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/platform";
    }

    @PostMapping("/games/{id}/toggle")
    public String toggleGame(@PathVariable Long id, RedirectAttributes redirect) {
        games.platformToggle(id);
        redirect.addFlashAttribute("success", "게임 공개 상태를 변경했습니다.");
        return "redirect:/platform";
    }

    @PostMapping
    public String multiplex(@RequestParam String action,
                            @RequestParam(required = false) Long makerId,
                            @RequestParam(required = false) Long gameId,
                            Authentication authentication, RedirectAttributes redirect) {
        if ("toggleMaker".equals(action) && makerId != null) {
            return toggleAccount(makerId, authentication, redirect);
        }
        if ("toggleGame".equals(action) && gameId != null) {
            return toggleGame(gameId, redirect);
        }
        redirect.addFlashAttribute("error", "알 수 없는 관리 요청입니다.");
        return "redirect:/platform";
    }
}
