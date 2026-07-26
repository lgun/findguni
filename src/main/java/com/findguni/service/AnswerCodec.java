package com.findguni.service;

import com.findguni.model.PuzzleType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class AnswerCodec {
    private final byte[] secret;

    public AnswerCodec(@Value("${findguni.answers.hmac-secret}") String secret) {
        if (secret == null || secret.length() < 16) {
            throw new IllegalStateException("정답 HMAC secret은 최소 16자여야 합니다.");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String digest(PuzzleType type, String answer) {
        String normalized = normalize(type, answer);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("정답을 안전하게 처리하지 못했습니다.", e);
        }
    }

    public boolean matches(PuzzleType type, String submitted, String expectedDigest) {
        String normalized = normalize(type, submitted);
        if (type == PuzzleType.TEXT_ANSWER
                && normalized.codePointCount(0, normalized.length()) >= 30) {
            return true;
        }
        if (expectedDigest == null) return type == PuzzleType.STORY;
        byte[] actual = digest(type, normalized).getBytes(StandardCharsets.US_ASCII);
        byte[] expected = expectedDigest.getBytes(StandardCharsets.US_ASCII);
        return java.security.MessageDigest.isEqual(actual, expected);
    }

    public String normalize(PuzzleType type, String answer) {
        String value = Normalizer.normalize(Objects.toString(answer, ""), Normalizer.Form.NFKC).trim();
        return switch (type) {
            case STORY -> "";
            case NUMBER_LOCK, KEYPAD -> value.replaceAll("[^0-9]", "");
            case ALPHABET_LOCK -> value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
            case DIRECTION_LOCK -> normalizeSequence(value, directionAliases());
            case COLOR_LOCK -> normalizeSequence(value, colorAliases());
            case MULTIPLE_CHOICE, TEXT_ANSWER -> value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        };
    }

    private String normalizeSequence(String value, Map<String, String> aliases) {
        String prepared = value.toUpperCase(Locale.ROOT)
                .replace("↑", ",UP,").replace("→", ",RIGHT,")
                .replace("↓", ",DOWN,").replace("←", ",LEFT,")
                .replace("위", ",UP,").replace("오른쪽", ",RIGHT,")
                .replace("아래", ",DOWN,").replace("왼쪽", ",LEFT,")
                .replace("빨강", ",RED,").replace("초록", ",GREEN,")
                .replace("파랑", ",BLUE,").replace("노랑", ",YELLOW,")
                .replace("보라", ",PURPLE,").replace("주황", ",ORANGE,");
        return Arrays.stream(prepared.split("[,\\s>;/|-]+"))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .map(token -> aliases.getOrDefault(token, token))
                .collect(Collectors.joining(","));
    }

    private Map<String, String> directionAliases() {
        Map<String, String> map = new HashMap<>();
        map.put("U", "UP"); map.put("N", "UP"); map.put("UP", "UP");
        map.put("R", "RIGHT"); map.put("E", "RIGHT"); map.put("RIGHT", "RIGHT");
        map.put("D", "DOWN"); map.put("S", "DOWN"); map.put("DOWN", "DOWN");
        map.put("L", "LEFT"); map.put("W", "LEFT"); map.put("LEFT", "LEFT");
        return map;
    }

    private Map<String, String> colorAliases() {
        Map<String, String> map = new HashMap<>();
        for (String color : List.of("RED", "GREEN", "BLUE", "YELLOW", "PURPLE", "ORANGE")) map.put(color, color);
        map.put("R", "RED"); map.put("G", "GREEN"); map.put("B", "BLUE");
        map.put("Y", "YELLOW"); map.put("P", "PURPLE"); map.put("O", "ORANGE");
        return map;
    }
}
