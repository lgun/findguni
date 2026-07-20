package com.findguni.service;

import com.findguni.model.*;
import com.findguni.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Locale;

@Service
public class AccountService {
    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;

    public AccountService(UserAccountRepository accounts, PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserAccount signupMaker(String email, String password, String confirmPassword, String displayName) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.length() > 190 || !normalizedEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("올바른 이메일 주소를 입력해 주세요.");
        }
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw new IllegalArgumentException("비밀번호는 8자 이상 72자 이하로 입력해 주세요.");
        }
        if (!password.equals(confirmPassword)) {
            throw new IllegalArgumentException("비밀번호 확인이 일치하지 않습니다.");
        }
        String safeName = cleanRequired(displayName, "표시 이름", 80);
        if (accounts.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }
        return accounts.save(new UserAccount(normalizedEmail, passwordEncoder.encode(password), safeName, Role.MAKER));
    }

    @Transactional(readOnly = true)
    public UserAccount current(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        UserAccount account = accounts.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!account.isActive()) {
            throw new org.springframework.security.access.AccessDeniedException("정지된 계정입니다.");
        }
        return account;
    }

    @Transactional(readOnly = true)
    public List<UserAccount> makers() {
        return accounts.findAllByRoleOrderByCreatedAtDesc(Role.MAKER);
    }

    @Transactional
    public UserAccount toggleStatus(Long id, Long actingAdminId) {
        UserAccount account = accounts.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (account.getId().equals(actingAdminId)) {
            throw new IllegalArgumentException("현재 로그인한 관리자 계정은 정지할 수 없습니다.");
        }
        account.setStatus(account.getStatus() == AccountStatus.ACTIVE
                ? AccountStatus.SUSPENDED : AccountStatus.ACTIVE);
        return account;
    }

    @Transactional
    public UserAccount ensureSeedAccount(String email, String password, String displayName, Role role) {
        String normalizedEmail = normalizeEmail(email);
        return accounts.findByEmailIgnoreCase(normalizedEmail).orElseGet(() ->
                accounts.save(new UserAccount(normalizedEmail, passwordEncoder.encode(password), displayName, role)));
    }

    @Transactional
    public UserAccount ensureDemoSeedAccount(String email, String password, String displayName) {
        String normalizedEmail = normalizeEmail(email);
        return accounts.findByEmailIgnoreCase(normalizedEmail).map(account -> {
            if (!passwordEncoder.matches(password, account.getPasswordHash())) {
                account.setPasswordHash(passwordEncoder.encode(password));
            }
            return account;
        }).orElseGet(() -> accounts.save(new UserAccount(normalizedEmail,
                passwordEncoder.encode(password), displayName, Role.MAKER)));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String cleanRequired(String value, String label, int max) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.isEmpty() || cleaned.length() > max) {
            throw new IllegalArgumentException(label + "을(를) 1자 이상 " + max + "자 이하로 입력해 주세요.");
        }
        return cleaned;
    }
}
