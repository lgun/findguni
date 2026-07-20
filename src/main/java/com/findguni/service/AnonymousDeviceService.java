package com.findguni.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Component
public class AnonymousDeviceService {
    public static final String COOKIE_NAME = "FINDGUNI_DEVICE";
    private final SecureRandom secureRandom = new SecureRandom();
    private final boolean secureCookie;

    public AnonymousDeviceService(@Value("${findguni.player-cookie.secure:false}") boolean secureCookie) {
        this.secureCookie = secureCookie;
    }

    public Optional<String> token(HttpServletRequest request) {
        if (request.getCookies() == null) return Optional.empty();
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName()) && cookie.getValue() != null && cookie.getValue().length() >= 32) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    public String ensureToken(HttpServletRequest request, HttpServletResponse response) {
        return token(request).orElseGet(() -> {
            byte[] random = new byte[32];
            secureRandom.nextBytes(random);
            String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, raw)
                    .httpOnly(true).secure(secureCookie).sameSite("Lax").path("/")
                    .maxAge(Duration.ofDays(365)).build();
            response.addHeader("Set-Cookie", cookie.toString());
            return raw;
        });
    }

    public String hash(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("기기 토큰을 처리하지 못했습니다.", e);
        }
    }
}
