package com.findguni.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.findguni.model.Difficulty;
import com.findguni.model.EscapeGame;
import com.findguni.model.GameStage;
import com.findguni.model.GameTheme;
import com.findguni.model.GameVisibility;
import com.findguni.model.StoryEffect;
import com.findguni.model.UserAccount;
import com.findguni.service.AccountService;
import com.findguni.service.AnonymousDeviceService;
import com.findguni.service.GameAuthoringService;
import com.findguni.service.OpenverseAudioService;
import com.findguni.service.PublishingService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "findguni.seed.admin.enabled=false",
        "findguni.seed.demo.enabled=false",
        "findguni.answers.hmac-secret=test-answer-secret-with-enough-length",
        "findguni.public-base-url=https://escape.test",
        "spring.datasource.url=jdbc:h2:mem:findguni-audio-story-web-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.thymeleaf.cache=false",
        "spring.jpa.show-sql=false",
        "debug=false",
        "logging.level.org.hibernate.SQL=OFF",
        "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
@AutoConfigureMockMvc
@Transactional
@Import(AudioStoryWebIntegrationTest.OpenverseStubConfig.class)
class AudioStoryWebIntegrationTest {

    private static final Path UPLOAD_DIR = createUploadDirectory();
    private static final byte[] MP3 = new byte[] {
            'I', 'D', '3', 4, 0, 0, 0, 0, 0, 2, 0x11, 0x22
    };

    @DynamicPropertySource
    static void uploadProperties(DynamicPropertyRegistry registry) {
        registry.add("findguni.upload-dir", () -> UPLOAD_DIR.toString());
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccountService accounts;

    @Autowired
    private GameAuthoringService authoring;

    @Autowired
    private PublishingService publishing;

    @Autowired
    private StubOpenverseAudioService openverseAudio;

    @Test
    void crossMakerBgmSceneAndSfxUploadsReturn404BeforeAnyFileIsStored() throws Exception {
        UserAccount owner = signup("media-owner");
        UserAccount intruder = signup("media-intruder");
        EscapeGame game = createGame(owner, "media-owned");
        GameStage stage = authoring.stages(game.getId(), owner).get(0);
        MockHttpSession browser = login(intruder.getEmail());
        CsrfToken csrf = csrf("/maker", browser);
        long filesBefore = regularFileCount();

        MockMultipartFile attemptedBgm = new MockMultipartFile(
                "bgmFile", "not-mine.mp3", "audio/mpeg", MP3);
        mvc.perform(multipart("/maker/games/{id}/edit", game.getId())
                        .file(attemptedBgm)
                        .session(browser)
                        .param(csrf.getParameterName(), csrf.getToken())
                        .param("title", "Not mine")
                        .param("slug", game.getSlug())
                        .param("theme", "MIDNIGHT")
                        .param("difficulty", "NORMAL")
                        .param("estimatedMinutes", "30")
                        .param("visibility", "LINK_ONLY"))
                .andExpect(status().isNotFound());

        MockMultipartFile attemptedScene = new MockMultipartFile(
                "scenePhoto", "not-mine.png", "image/png", pngBytes());
        MockMultipartFile attemptedSfx = new MockMultipartFile(
                "sfxFile", "not-mine.ogg", "audio/ogg", new byte[] {'O', 'g', 'g', 'S', 0, 2});
        mvc.perform(multipart("/maker/games/{gameId}/stages/{stageId}", game.getId(), stage.getId())
                        .file(attemptedScene)
                        .file(attemptedSfx)
                        .session(browser)
                        .param(csrf.getParameterName(), csrf.getToken())
                        .param("title", "Not mine")
                        .param("puzzleType", "STORY"))
                .andExpect(status().isNotFound());

        assertThat(regularFileCount()).isEqualTo(filesBefore);
    }

    @Test
    void directBgmUploadIsServedWithAudioTypeAndByteRanges() throws Exception {
        UserAccount owner = signup("direct-audio");
        EscapeGame game = createGame(owner, "direct-audio");
        MockHttpSession browser = login(owner.getEmail());
        CsrfToken csrf = csrf("/maker/games/" + game.getId() + "/edit", browser);
        MockMultipartFile bgm = new MockMultipartFile(
                "bgmFile", "ambient.bin", "application/octet-stream", MP3);

        mvc.perform(multipart("/maker/games/{id}/edit", game.getId())
                        .file(bgm)
                        .session(browser)
                        .param(csrf.getParameterName(), csrf.getToken())
                        .param("title", game.getTitle())
                        .param("slug", game.getSlug())
                        .param("bgmTitle", "Direct ambience")
                        .param("bgmVolume", "0.4")
                        .param("bgmLoop", "true")
                        .param("storyTextSpeed", "35")
                        .param("enableVignette", "true")
                        .param("theme", "MIDNIGHT")
                        .param("difficulty", "NORMAL")
                        .param("estimatedMinutes", "30")
                        .param("visibility", "LINK_ONLY"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/maker/games/" + game.getId() + "/edit"));

        String mediaUrl = authoring.ownedGame(game.getId(), owner).getBgmUrl();
        assertThat(mediaUrl).matches("/uploads/audio/[0-9a-f-]{36}\\.mp3");
        mvc.perform(get(mediaUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.valueOf("audio/mpeg")))
                .andExpect(content().bytes(MP3));
        mvc.perform(get(mediaUrl).header(HttpHeaders.RANGE, "bytes=0-2"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-2/" + MP3.length))
                .andExpect(content().contentType(MediaType.valueOf("audio/mpeg")))
                .andExpect(content().bytes(new byte[] {'I', 'D', '3'}));
    }

    @Test
    void openverseSearchRequiresMakerAndReturnsOnlyMockedJsonFields() throws Exception {
        OpenverseAudioService.AudioSearchResult result = new OpenverseAudioService.AudioSearchResult(
                "ov-1", "Rain", "Field Artist", "by",
                "https://license.example.test/by", "Rain by Field Artist",
                "https://media.example.test/rain.mp3", "https://source.example.test/rain", 1234L);
        openverseAudio.respondWith(new OpenverseAudioService.AudioSearchResponse(List.of(result)));

        mvc.perform(get("/maker/audio/search").param("q", "rain").param("kind", "BGM"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/login")));

        UserAccount maker = signup("openverse-maker");
        MockHttpSession browser = login(maker.getEmail());
        mvc.perform(get("/maker/audio/search")
                        .session(browser)
                        .param("q", "rain")
                        .param("kind", "BGM"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.results[0].id").value("ov-1"))
                .andExpect(jsonPath("$.results[0].title").value("Rain"))
                .andExpect(jsonPath("$.results[0].creator").value("Field Artist"))
                .andExpect(jsonPath("$.results[0].license").value("by"))
                .andExpect(jsonPath("$.results[0].licenseUrl").value("https://license.example.test/by"))
                .andExpect(jsonPath("$.results[0].attribution").value("Rain by Field Artist"))
                .andExpect(jsonPath("$.results[0].audioUrl").value("https://media.example.test/rain.mp3"))
                .andExpect(jsonPath("$.results[0].sourceUrl").value("https://source.example.test/rain"))
                .andExpect(jsonPath("$.results[0].durationMs").value(1234));
        assertThat(openverseAudio.lastQuery).isEqualTo("rain");
        assertThat(openverseAudio.lastKind).isEqualTo(OpenverseAudioService.AudioKind.BGM);
        assertThat(openverseAudio.callCount).isEqualTo(1);
    }

    @Test
    void builderAndPublishedPlayerRenderStorySoundControlsAndAttribution() throws Exception {
        UserAccount owner = signup("render-audio");
        EscapeGame game = createGame(owner, "render-audio");
        authoring.updateSettings(game.getId(), owner, game.getTitle(), game.getSlug(), "summary", "intro", null,
                "#112233", "#445566", "#778899", "music",
                true, true, true, GameTheme.MIDNIGHT, Difficulty.NORMAL, 30, GameVisibility.LINK_ONLY,
                "https://media.example.test/ambient.mp3", "Night ambience", "BGM Artist", "CC BY 4.0",
                "https://license.example.test/by", "https://source.example.test/night",
                0.45, true, 44, false);
        GameStage stage = authoring.stages(game.getId(), owner).get(0);
        authoring.updateStage(stage.getId(), owner, new GameAuthoringService.StageDraft(
                "Opening room", "The lights suddenly flicker.", "Find the exit", "Listen closely",
                stage.getPuzzleType(), stage.getDraftAnswer(), stage.getOptionsText(), stage.getLockLength(),
                stage.getRequiredItem(), stage.getRewardItem(), StoryEffect.GLITCH,
                "https://media.example.test/scene.png", "https://media.example.test/door.ogg",
                "Door slam", "Foley Artist", "CC0",
                "https://license.example.test/cc0", "https://source.example.test/door", 0.7));

        MockHttpSession makerBrowser = login(owner.getEmail());
        mvc.perform(get("/maker/games/{id}/edit", game.getId()).session(makerBrowser))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("storyEffects"))
                .andExpect(content().string(containsString("name=\"bgmFile\"")))
                .andExpect(content().string(containsString("name=\"bgmVolume\"")))
                .andExpect(content().string(containsString("name=\"storyTextSpeed\"")))
                .andExpect(content().string(containsString("name=\"storyEffect\"")))
                .andExpect(content().string(containsString("name=\"scenePhoto\"")))
                .andExpect(content().string(containsString("name=\"sfxFile\"")))
                .andExpect(content().string(containsString("data-audio-kind=\"BGM\"")))
                .andExpect(content().string(containsString("data-audio-kind=\"SFX\"")));

        publishing.publish(game.getId(), owner);
        MockHttpSession playerBrowser = new MockHttpSession();
        CsrfToken csrf = csrf("/play/" + game.getSlug(), playerBrowser);
        MvcResult start = mvc.perform(post("/play/{slug}/start", game.getSlug())
                        .session(playerBrowser)
                        .param(csrf.getParameterName(), csrf.getToken()))
                .andExpect(status().is3xxRedirection())
                .andExpect(cookie().exists(AnonymousDeviceService.COOKIE_NAME))
                .andReturn();
        jakarta.servlet.http.Cookie device = start.getResponse().getCookie(AnonymousDeviceService.COOKIE_NAME);
        assertThat(device).isNotNull();

        mvc.perform(get("/play/{slug}/stage", game.getSlug()).session(playerBrowser).cookie(device))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-story-effect=\"GLITCH\"")))
                .andExpect(content().string(containsString("data-story-speed=\"44\"")))
                .andExpect(content().string(containsString("data-vignette=\"false\"")))
                .andExpect(content().string(containsString("https://media.example.test/scene.png")))
                .andExpect(content().string(containsString("The lights suddenly flicker.")))
                .andExpect(content().string(containsString("data-audio-controller")))
                .andExpect(content().string(containsString("data-bgm-track")))
                .andExpect(content().string(containsString("data-sfx-track")))
                .andExpect(content().string(containsString("Night ambience")))
                .andExpect(content().string(containsString("BGM Artist")))
                .andExpect(content().string(containsString("Door slam")))
                .andExpect(content().string(containsString("Foley Artist")))
                .andExpect(content().string(containsString("https://license.example.test/by")))
                .andExpect(content().string(containsString("https://source.example.test/night")))
                .andExpect(content().string(containsString("https://license.example.test/cc0")))
                .andExpect(content().string(containsString("https://source.example.test/door")));
    }

    private CsrfToken csrf(String page, MockHttpSession session) throws Exception {
        MvcResult result = mvc.perform(get(page).session(session))
                .andExpect(status().isOk())
                .andReturn();
        Object token = result.getRequest().getAttribute("_csrf");
        assertThat(token).isInstanceOf(CsrfToken.class);
        return (CsrfToken) token;
    }

    private MockHttpSession login(String email) throws Exception {
        MockHttpSession browser = new MockHttpSession();
        CsrfToken token = csrf("/login", browser);
        MvcResult result = mvc.perform(post("/login")
                        .session(browser)
                        .param(token.getParameterName(), token.getToken())
                        .param("email", email)
                        .param("password", "password-123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/maker"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private UserAccount signup(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return accounts.signupMaker(prefix + "+" + suffix + "@example.com",
                "password-123", "password-123", prefix);
    }

    private EscapeGame createGame(UserAccount owner, String prefix) {
        return authoring.create(owner, prefix, prefix + "-" + UUID.randomUUID(), "QUICK_10",
                GameTheme.MIDNIGHT, Difficulty.NORMAL, 30);
    }

    private long regularFileCount() throws IOException {
        try (var paths = Files.walk(UPLOAD_DIR)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    private byte[] pngBytes() throws IOException {
        BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, Color.RED.getRGB());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) throw new IOException("PNG writer unavailable");
        return output.toByteArray();
    }

    private static Path createUploadDirectory() {
        try {
            return Files.createTempDirectory("findguni-audio-story-web-").toAbsolutePath().normalize();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @AfterAll
    static void deleteUploadDirectory() throws IOException {
        if (!Files.exists(UPLOAD_DIR) || !Files.isDirectory(UPLOAD_DIR)) return;
        try (var paths = Files.walk(UPLOAD_DIR)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (path.startsWith(UPLOAD_DIR)) Files.deleteIfExists(path);
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OpenverseStubConfig {
        @Bean
        @Primary
        StubOpenverseAudioService stubOpenverseAudioService(ObjectMapper objectMapper) {
            return new StubOpenverseAudioService(objectMapper);
        }
    }

    static class StubOpenverseAudioService extends OpenverseAudioService {
        private AudioSearchResponse response = new AudioSearchResponse(List.of());
        private String lastQuery;
        private AudioKind lastKind;
        private int callCount;

        StubOpenverseAudioService(ObjectMapper objectMapper) {
            super(objectMapper);
        }

        void respondWith(AudioSearchResponse response) {
            this.response = response;
            this.lastQuery = null;
            this.lastKind = null;
            this.callCount = 0;
        }

        @Override
        public AudioSearchResponse search(String query, AudioKind kind) {
            this.lastQuery = query;
            this.lastKind = kind;
            this.callCount++;
            return response;
        }
    }
}
