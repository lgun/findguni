package com.findguni.controller;

import com.findguni.model.Difficulty;
import com.findguni.model.EscapeGame;
import com.findguni.model.GameItem;
import com.findguni.model.GameFlowMode;
import com.findguni.model.GameStage;
import com.findguni.model.GameTheme;
import com.findguni.model.ItemType;
import com.findguni.model.StageEntryMode;
import com.findguni.model.UserAccount;
import com.findguni.repository.ScannedClueRepository;
import com.findguni.service.AccountService;
import com.findguni.service.AnonymousDeviceService;
import com.findguni.service.GameAuthoringService;
import com.findguni.service.PublishingService;
import com.findguni.service.QRCodeService;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "findguni.seed.admin.enabled=false",
        "findguni.seed.demo.enabled=false",
        "findguni.answers.hmac-secret=test-answer-secret-with-enough-length",
        "findguni.public-base-url=https://escape.test",
        "spring.datasource.url=jdbc:h2:mem:findguni-tools-web-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.thymeleaf.cache=false",
        "spring.jpa.show-sql=false",
        "debug=false",
        "logging.level.org.hibernate.SQL=OFF",
        "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
@AutoConfigureMockMvc
@Transactional
class MakerAndPlayerToolsWebIntegrationTest {

    private static final Path UPLOAD_DIR = createUploadDirectory();

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
    private QRCodeService qrCodes;

    @Autowired
    private ScannedClueRepository scannedClues;

    @Test
    void makerBuilderRendersNewFieldsAndOtherMakerCannotUpdatePhotoOrReadItemQr() throws Exception {
        UserAccount owner = signup("route-owner");
        UserAccount intruder = signup("route-intruder");
        EscapeGame game = createGame(owner, "owned-routes");
        GameItem item = authoring.addItem(game.getId(), owner, ItemType.PHOTO, "Private photo clue",
                "owner only", "read the sign", "📸", true, null);
        MockHttpSession ownerBrowser = login(owner.getEmail(), "password-123");
        MockHttpSession intruderBrowser = login(intruder.getEmail(), "password-123");

        mvc.perform(get("/maker/games/{id}/edit", game.getId()).session(ownerBrowser))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("game", "stages", "items", "itemTypes", "puzzleTypes"))
                .andExpect(content().string(containsString("name=\"secondaryColor\"")))
                .andExpect(content().string(containsString("name=\"backgroundColor\"")))
                .andExpect(content().string(containsString("name=\"gameIcon\"")))
                .andExpect(content().string(containsString("name=\"allowNotebook\"")))
                .andExpect(content().string(containsString("name=\"allowCluebook\"")))
                .andExpect(content().string(containsString("name=\"allowQrScanner\"")))
                .andExpect(content().string(containsString("name=\"unlimitedHints\"")))
                .andExpect(content().string(containsString("name=\"hintLimit\"")))
                .andExpect(content().string(containsString("name=\"hintCooldownSeconds\"")))
                .andExpect(content().string(containsString("name=\"itemType\"")))
                .andExpect(content().string(containsString("name=\"clueText\"")))
                .andExpect(content().string(containsString("name=\"initiallyOwned\"")))
                .andExpect(content().string(containsString("name=\"copyableText\"")))
                .andExpect(content().string(containsString("name=\"alternateRequiredItem\"")))
                .andExpect(content().string(containsString("name=\"alternateScanText\"")))
                .andExpect(content().string(containsString("name=\"qrEnabled\"")))
                .andExpect(content().string(containsString("name=\"requiredItemIds\"")))
                .andExpect(content().string(containsString("name=\"consumeRequiredItems\"")))
                .andExpect(content().string(containsString("name=\"photo\"")));

        mvc.perform(get("/maker/games/{gameId}/items/{itemId}/qr", game.getId(), item.getId())
                        .session(ownerBrowser))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
        mvc.perform(get("/maker/games/{gameId}/items/{itemId}/qr", game.getId(), item.getId())
                        .session(intruderBrowser))
                .andExpect(status().isNotFound());

        CsrfToken intruderCsrf = csrf("/maker", intruderBrowser);
        MockMultipartFile attemptedPhoto = new MockMultipartFile(
                "photo", "stolen.png", "image/png", pngBytes());
        mvc.perform(multipart("/maker/games/{gameId}/items/{itemId}", game.getId(), item.getId())
                        .file(attemptedPhoto)
                        .session(intruderBrowser)
                        .param(intruderCsrf.getParameterName(), intruderCsrf.getToken())
                        .param("itemType", "PHOTO")
                        .param("name", "stolen")
                        .param("description", "stolen")
                        .param("clueText", "stolen")
                        .param("icon", "x")
                        .param("qrEnabled", "true"))
                .andExpect(status().isNotFound());
        assertThat(authoring.ownedItem(game.getId(), item.getId(), owner).getImageUrl()).isNull();
    }

    @Test
    void makerGetsAnOwnedA4QrKitAsOnePdfOrOriginalPngZip() throws Exception {
        UserAccount owner = signup("qr-kit-owner");
        UserAccount intruder = signup("qr-kit-intruder");
        EscapeGame game = createGame(owner, "qr-kit-game");
        authoring.updateFlowMode(game.getId(), owner, GameFlowMode.QR_EXPLORATION);
        GameStage stage = authoring.stages(game.getId(), owner).get(0);
        stage.setEntryMode(StageEntryMode.QR);
        authoring.addItem(game.getId(), owner, ItemType.EVIDENCE, "숨은 단서",
                "QR kit clue", "스캔해서 찾는 단서", "🔎", true, null);
        int expectedQrCount = 1
                + (int) authoring.stages(game.getId(), owner).stream().filter(GameStage::isQrEnabled).count()
                + (int) authoring.items(game.getId(), owner).stream().filter(GameItem::isQrEnabled).count();
        MockHttpSession ownerBrowser = login(owner.getEmail(), "password-123");
        MockHttpSession intruderBrowser = login(intruder.getEmail(), "password-123");

        mvc.perform(get("/maker/games/{id}/qr-kit", game.getId()).session(ownerBrowser))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("game", "kit", "qrCards", "qrPages"))
                .andExpect(content().string(containsString("QR 일괄 다운로드·인쇄")))
                .andExpect(content().string(containsString("PDF 한 문서 받기")))
                .andExpect(content().string(containsString("PNG 전체 ZIP")))
                .andExpect(content().string(containsString("숨은 단서")));

        byte[] pdf = mvc.perform(get("/maker/games/{id}/qr-kit/print.pdf", game.getId())
                        .session(ownerBrowser))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            BufferedImage printedPage = new PDFRenderer(document).renderImageWithDPI(0, 150);
            Set<String> printedPayloads = new HashSet<>();
            int sourcePageWidth = 1240;
            int sourcePageHeight = 1754;
            int sourceMargin = 48;
            int sourceGap = 20;
            int sourceCardWidth = (sourcePageWidth - sourceMargin * 2 - sourceGap) / 2;
            int sourceCardHeight = (sourcePageHeight - sourceMargin * 2 - sourceGap * 2) / 3;
            for (int index = 0; index < expectedQrCount; index++) {
                int sourceCardX = sourceMargin + index % 2 * (sourceCardWidth + sourceGap);
                int sourceCardY = sourceMargin + index / 2 * (sourceCardHeight + sourceGap);
                int cropX = (int) Math.round((sourceCardX + (sourceCardWidth - 320) / 2.0)
                        * printedPage.getWidth() / sourcePageWidth);
                int cropY = (int) Math.round((sourceCardY + 55.0)
                        * printedPage.getHeight() / sourcePageHeight);
                int cropWidth = (int) Math.round(320.0 * printedPage.getWidth() / sourcePageWidth);
                int cropHeight = (int) Math.round(320.0 * printedPage.getHeight() / sourcePageHeight);
                BufferedImage printedQr = printedPage.getSubimage(cropX, cropY, cropWidth, cropHeight);
                BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(
                        new BufferedImageLuminanceSource(printedQr)));
                printedPayloads.add(new MultiFormatReader().decode(bitmap,
                        java.util.Map.of(DecodeHintType.TRY_HARDER, true)).getText());
            }
            assertThat(printedPayloads).hasSize(expectedQrCount).contains(qrCodes.gameStartUrl(game));
        }

        byte[] zipBytes = mvc.perform(get("/maker/games/{id}/qr-kit/qr-images.zip", game.getId())
                        .session(ownerBrowser))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andReturn().getResponse().getContentAsByteArray();
        Set<String> entries = new HashSet<>();
        int decodedQrCount = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes),
                java.nio.charset.StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.add(entry.getName());
                if (!entry.getName().endsWith(".png")) continue;
                ByteArrayOutputStream imageBytes = new ByteArrayOutputStream();
                zip.transferTo(imageBytes);
                MockMultipartFile qrImage = new MockMultipartFile(
                        "frame", entry.getName(), "image/png", imageBytes.toByteArray());
                assertThat(qrCodes.decode(qrImage)).isPresent();
                decodedQrCount++;
            }
        }
        assertThat(decodedQrCount).isEqualTo(expectedQrCount);
        assertThat(entries).contains("QR-목록.txt");

        mvc.perform(get("/maker/games/{id}/qr-kit", game.getId()).session(intruderBrowser))
                .andExpect(status().isNotFound());
    }

    @Test
    void makerPhotoUploadIsServedBackAsARealImageFromTheReturnedUrl() throws Exception {
        UserAccount owner = signup("upload-owner");
        EscapeGame game = createGame(owner, "upload-game");
        MockHttpSession ownerBrowser = login(owner.getEmail(), "password-123");
        CsrfToken csrf = csrf("/maker/games/" + game.getId() + "/edit", ownerBrowser);
        MockMultipartFile photo = new MockMultipartFile(
                "photo", "camera-capture.png", "image/png", pngBytes());

        mvc.perform(multipart("/maker/games/{id}/items", game.getId())
                        .file(photo)
                        .session(ownerBrowser)
                        .param(csrf.getParameterName(), csrf.getToken())
                        .param("itemType", "PHOTO")
                        .param("name", "Captured clue")
                        .param("description", "Taken on site")
                        .param("clueText", "Count the windows")
                        .param("icon", "📷")
                        .param("qrEnabled", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl())
                        .startsWith("/maker/games/" + game.getId() + "/edit?tab=items&item="));

        GameItem stored = authoring.items(game.getId(), owner).stream()
                .filter(item -> item.getName().equals("Captured clue"))
                .findFirst()
                .orElseThrow();
        assertThat(stored.getImageUrl()).matches("/uploads/[0-9a-f-]{36}\\.png");

        MvcResult image = mvc.perform(get(stored.getImageUrl()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn();
        assertThat(ImageIO.read(new ByteArrayInputStream(image.getResponse().getContentAsByteArray())))
                .isNotNull();
    }

    @Test
    void builderHiddenFalseFallbackStillPersistsCheckedAndUncheckedToolFlagsCorrectly() throws Exception {
        UserAccount owner = signup("flags-owner");
        EscapeGame game = createGame(owner, "flags-game");
        MockHttpSession browser = login(owner.getEmail(), "password-123");
        CsrfToken csrf = csrf("/maker/games/" + game.getId() + "/edit", browser);

        mvc.perform(post("/maker/games/{id}/edit", game.getId())
                        .session(browser)
                        .param(csrf.getParameterName(), csrf.getToken())
                        .param("title", game.getTitle())
                        .param("slug", game.getSlug())
                        .param("accentColor", "#123456")
                        .param("secondaryColor", "#654321")
                        .param("backgroundColor", "#101820")
                        .param("gameIcon", "🧭")
                        .param("allowNotebook", "false", "true")
                        .param("allowCluebook", "false")
                        .param("allowQrScanner", "false")
                        .param("unlimitedHints", "false")
                        .param("hintLimit", "2")
                        .param("hintCooldownSeconds", "45")
                        .param("theme", "MIDNIGHT")
                        .param("difficulty", "NORMAL")
                        .param("estimatedMinutes", "30")
                        .param("visibility", "LINK_ONLY"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/maker/games/" + game.getId() + "/edit"));

        EscapeGame updated = authoring.ownedGame(game.getId(), owner);
        assertThat(updated.isAllowNotebook()).isTrue();
        assertThat(updated.isAllowCluebook()).isFalse();
        assertThat(updated.isAllowQrScanner()).isFalse();
        assertThat(updated.isUnlimitedHints()).isFalse();
        assertThat(updated.getHintLimit()).isEqualTo(2);
        assertThat(updated.getHintCooldownSeconds()).isEqualTo(45);
    }

    @Test
    void multipartScanReturnsJsonStageRendersToolsAndFallbackClueRedirectWorks() throws Exception {
        UserAccount owner = signup("scan-owner");
        EscapeGame game = createGame(owner, "scan-game");
        GameItem item = authoring.addItem(game.getId(), owner, ItemType.EVIDENCE, "Scanned evidence",
                "A marked receipt", "The total is the lock code", "🧾", true, null);
        publishing.publish(game.getId(), owner);
        MockHttpSession browser = new MockHttpSession();
        CsrfToken csrf = csrf("/play/" + game.getSlug(), browser);

        MvcResult start = mvc.perform(post("/play/{slug}/start", game.getSlug())
                        .session(browser)
                        .param(csrf.getParameterName(), csrf.getToken()))
                .andExpect(status().is3xxRedirection())
                .andExpect(cookie().exists(AnonymousDeviceService.COOKIE_NAME))
                .andReturn();
        jakarta.servlet.http.Cookie device = start.getResponse().getCookie(AnonymousDeviceService.COOKIE_NAME);
        assertThat(device).isNotNull();

        mvc.perform(post("/play/{slug}/notes", game.getSlug())
                        .session(browser).cookie(device)
                        .param(csrf.getParameterName(), csrf.getToken())
                        .param("notes", "receipt total = 1357"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/play/" + game.getSlug() + "/stage"));

        String payload = qrCodes.itemClueUrl(game, item);
        mvc.perform(multipart("/play/{slug}/scan", game.getSlug())
                        .session(browser).cookie(device)
                        .param(csrf.getParameterName(), csrf.getToken())
                        .param("payload", payload))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.item.stableKey").value(item.getStableKey()));
        mvc.perform(multipart("/play/{slug}/scan", game.getSlug())
                        .session(browser).cookie(device)
                        .param(csrf.getParameterName(), csrf.getToken())
                        .param("payload", payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        MockMultipartFile qrFrame = new MockMultipartFile(
                "frame", "camera-frame.png", "image/png", qrCodes.generateForItem(game, item));
        mvc.perform(multipart("/play/{slug}/scan", game.getSlug())
                        .file(qrFrame)
                        .session(browser).cookie(device)
                        .param(csrf.getParameterName(), csrf.getToken()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.item.stableKey").value(item.getStableKey()));
        assertThat(scannedClues.count()).isEqualTo(1);

        mvc.perform(get("/play/{slug}/stage", game.getSlug()).session(browser).cookie(device))
                .andExpect(status().isOk())
                .andExpect(model().attribute("notes", "receipt total = 1357"))
                .andExpect(model().attribute("allowNotebook", true))
                .andExpect(model().attribute("allowCluebook", true))
                .andExpect(model().attribute("allowQrScanner", true))
                .andExpect(model().attribute("inventory", org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.hasProperty("stableKey", org.hamcrest.Matchers.is(item.getStableKey())))))
                .andExpect(model().attribute("scannedClues", org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.hasProperty("stableKey", org.hamcrest.Matchers.is(item.getStableKey())))))
                .andExpect(content().string(containsString("Scanned evidence")));

        EscapeGame otherGame = createGame(owner, "other-scan-game");
        GameItem otherItem = authoring.addItem(otherGame.getId(), owner, ItemType.KEY, "Other key",
                "other", "other", "🔑", true, null);
        publishing.publish(otherGame.getId(), owner);
        mvc.perform(multipart("/play/{slug}/scan", game.getSlug())
                        .session(browser).cookie(device)
                        .param(csrf.getParameterName(), csrf.getToken())
                        .param("payload", qrCodes.itemClueUrl(otherGame, otherItem)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(multipart("/play/{slug}/scan", game.getSlug())
                        .session(browser).cookie(device)
                        .param(csrf.getParameterName(), csrf.getToken())
                        .param("payload", "https://attacker.example/clue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));

        MockHttpSession fallbackBrowser = new MockHttpSession();
        MvcResult fallback = mvc.perform(get("/play/{slug}/clue/{stableKey}",
                        game.getSlug(), item.getStableKey()).session(fallbackBrowser))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/play/" + game.getSlug() + "/stage"))
                .andExpect(cookie().exists(AnonymousDeviceService.COOKIE_NAME))
                .andReturn();
        jakarta.servlet.http.Cookie fallbackDevice = fallback.getResponse()
                .getCookie(AnonymousDeviceService.COOKIE_NAME);
        mvc.perform(get("/play/{slug}/stage", game.getSlug())
                        .session(fallbackBrowser).cookie(fallbackDevice))
                .andExpect(status().isOk())
                .andExpect(model().attribute("scannedClues", org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.hasProperty("stableKey", org.hamcrest.Matchers.is(item.getStableKey())))));
    }

    private CsrfToken csrf(String page, MockHttpSession session) throws Exception {
        MvcResult result = mvc.perform(get(page).session(session))
                .andExpect(status().isOk())
                .andReturn();
        Object token = result.getRequest().getAttribute("_csrf");
        assertThat(token).isInstanceOf(CsrfToken.class);
        return (CsrfToken) token;
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MockHttpSession browser = new MockHttpSession();
        CsrfToken token = csrf("/login", browser);
        MvcResult result = mvc.perform(post("/login")
                        .session(browser)
                        .param(token.getParameterName(), token.getToken())
                        .param("email", email)
                        .param("password", password))
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

    private byte[] pngBytes() throws IOException {
        BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, Color.RED.getRGB());
        image.setRGB(1, 0, Color.GREEN.getRGB());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) throw new IOException("PNG writer unavailable");
        return output.toByteArray();
    }

    private static Path createUploadDirectory() {
        try {
            return Files.createTempDirectory("findguni-web-uploads-").toAbsolutePath().normalize();
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
}
