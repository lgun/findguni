package com.findguni.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class OpenverseAudioService {
    private static final URI API_ROOT = URI.create("https://api.openverse.org/v1/audio/");
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public OpenverseAudioService(ObjectMapper objectMapper) {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER).build(), objectMapper);
    }

    OpenverseAudioService(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public AudioSearchResponse search(String query, AudioKind kind) {
        String cleaned = query == null ? "" : query.trim();
        if (cleaned.isBlank()) throw new IllegalArgumentException("검색어를 입력해 주세요.");
        if (cleaned.length() > 100) throw new IllegalArgumentException("검색어는 100자 이하로 입력해 주세요.");
        if (kind == null) throw new IllegalArgumentException("오디오 종류를 선택해 주세요.");

        String category = kind == AudioKind.BGM ? "music" : "sound_effect";
        String queryString = "q=" + URLEncoder.encode(cleaned, StandardCharsets.UTF_8)
                + "&page_size=8&license=cc0,pdm,by&categories=" + category;
        URI uri = URI.create(API_ROOT + "?" + queryString);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "Findguni/1.0 OpenverseAudioSearch")
                .GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 429) {
                throw new OpenverseUnavailableException("오디오 검색 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new OpenverseUnavailableException("오디오 검색 서비스를 일시적으로 사용할 수 없습니다.");
            }
            return new AudioSearchResponse(parse(response.body()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OpenverseUnavailableException("오디오 검색이 중단되었습니다.", e);
        } catch (IOException | IllegalArgumentException e) {
            throw new OpenverseUnavailableException("오디오 검색 서비스를 일시적으로 사용할 수 없습니다.", e);
        }
    }

    private List<AudioSearchResult> parse(String body) throws IOException {
        JsonNode results = objectMapper.readTree(body).path("results");
        if (!results.isArray()) throw new IOException("Openverse 응답 형식이 올바르지 않습니다.");
        List<AudioSearchResult> values = new ArrayList<>();
        for (JsonNode node : results) {
            String audioUrl = httpsUrl(text(node, "url", 1000));
            if (audioUrl == null) continue;
            values.add(new AudioSearchResult(
                    text(node, "id", 100), fallback(text(node, "title", 200), "제목 없음"),
                    fallback(text(node, "creator", 200), "알 수 없음"), text(node, "license", 100),
                    httpsUrl(text(node, "license_url", 1000)), text(node, "attribution", 1000),
                    audioUrl, httpsUrl(text(node, "foreign_landing_url", 1000)), duration(node)));
            if (values.size() == 8) break;
        }
        return List.copyOf(values);
    }

    private String text(JsonNode node, String field, int maxLength) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        String text = value.asText("").trim();
        if (text.isBlank()) return null;
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private Long duration(JsonNode node) {
        JsonNode duration = node.get("duration");
        if (duration == null || !duration.isNumber() || duration.asDouble() < 0) return null;
        return Math.round(duration.asDouble());
    }

    private String httpsUrl(String value) {
        if (value == null) return null;
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) return null;
            return uri.toASCIIString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String fallback(String value, String fallback) {
        return value == null ? fallback : value;
    }

    public enum AudioKind {
        BGM, SFX;

        public static AudioKind parse(String value) {
            try {
                return AudioKind.valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("오디오 종류는 BGM 또는 SFX여야 합니다.");
            }
        }
    }

    public record AudioSearchResponse(List<AudioSearchResult> results) {
        public List<AudioSearchResult> getResults() { return results; }
    }

    public record AudioSearchResult(String id, String title, String creator, String license,
                                    String licenseUrl, String attribution, String audioUrl,
                                    String sourceUrl, Long durationMs) {}

    public static class OpenverseUnavailableException extends RuntimeException {
        public OpenverseUnavailableException(String message) { super(message); }
        public OpenverseUnavailableException(String message, Throwable cause) { super(message, cause); }
    }
}
