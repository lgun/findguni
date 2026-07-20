package com.findguni.controller;

import com.findguni.service.AccountService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {
    private final AccountService accounts;

    public AuthController(AccountService accounts) { this.accounts = accounts; }

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error,
                        @RequestParam(required = false) String registered,
                        Authentication authentication, Model model) {
        if (isLoggedIn(authentication)) return redirectFor(authentication);
        model.addAttribute("loginError", error != null);
        model.addAttribute("registered", registered != null);
        return "auth/login";
    }

    @GetMapping("/signup")
    public String signup(Authentication authentication) {
        return isLoggedIn(authentication) ? redirectFor(authentication) : "auth/signup";
    }

    @PostMapping("/signup")
    public String signup(@RequestParam String email,
                         @RequestParam String password,
                         @RequestParam(defaultValue = "") String confirmPassword,
                         @RequestParam String displayName,
                         Model model, RedirectAttributes redirect) {
        try {
            accounts.signupMaker(email, password, confirmPassword, displayName);
            redirect.addAttribute("registered", "true");
            return "redirect:/login";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("email", email);
            model.addAttribute("displayName", displayName);
            return "auth/signup";
        }
    }

    private boolean isLoggedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private String redirectFor(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        return admin ? "redirect:/platform" : "redirect:/maker";
    }
}
