package com.findguni.controller;

import com.findguni.service.GameAuthoringService;
import com.findguni.service.AccountService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.Locale;

@Controller
public class HomeController {
    private final GameAuthoringService games;
    private final AccountService accounts;

    public HomeController(GameAuthoringService games, AccountService accounts) {
        this.games = games;
        this.accounts = accounts;
    }

    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        var publicGames = games.publicGames();
        model.addAttribute("games", publicGames);
        model.addAttribute("featuredGames", publicGames.stream().limit(6).toList());
        addCurrentUser(authentication, model);
        return "home";
    }

    @GetMapping("/games")
    public String games(@RequestParam(defaultValue = "") String q,
                        @RequestParam(defaultValue = "") String difficulty,
                        Authentication authentication, Model model) {
        String query = q.trim().toLowerCase(Locale.ROOT);
        var filtered = games.publicGames().stream()
                .filter(game -> query.isBlank()
                        || game.getTitle().toLowerCase(Locale.ROOT).contains(query)
                        || game.getSummary().toLowerCase(Locale.ROOT).contains(query)
                        || game.getOwner().getDisplayName().toLowerCase(Locale.ROOT).contains(query))
                .filter(game -> difficulty.isBlank() || game.getDifficulty().name().equalsIgnoreCase(difficulty))
                .toList();
        model.addAttribute("games", filtered);
        model.addAttribute("q", q);
        model.addAttribute("difficulty", difficulty.toUpperCase(Locale.ROOT));
        addCurrentUser(authentication, model);
        return "games";
    }

    private void addCurrentUser(Authentication authentication, Model model) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            var account = accounts.current(authentication);
            model.addAttribute("currentUser", account);
            if (account.getRole() == com.findguni.model.Role.MAKER) model.addAttribute("maker", account);
        }
    }
}
