package com.findguni.service;

import com.findguni.model.Difficulty;
import com.findguni.model.EscapeGame;
import com.findguni.model.GameItem;
import com.findguni.model.GameTheme;
import com.findguni.model.GameVisibility;
import com.findguni.model.ItemType;
import com.findguni.model.PlaySession;
import com.findguni.model.UserAccount;
import com.findguni.repository.ScannedClueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "findguni.seed.admin.enabled=false",
        "findguni.seed.demo.enabled=false",
        "findguni.answers.hmac-secret=test-answer-secret-with-enough-length",
        "findguni.public-base-url=https://escape.test",
        "spring.datasource.url=jdbc:h2:mem:findguni-tools-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "debug=false",
        "logging.level.org.hibernate.SQL=OFF",
        "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
@Transactional
class PlayerToolsIntegrationTest {

    @Autowired
    private AccountService accounts;

    @Autowired
    private GameAuthoringService authoring;

    @Autowired
    private PublishingService publishing;

    @Autowired
    private PlayService plays;

    @Autowired
    private QRCodeService qrCodes;

    @Autowired
    private ScannedClueRepository scannedClues;

    @Test
    void qrScanAddsOneInventoryItemAndOneClueAndRestartGetsFreshNotesAndTools() {
        PublishedGame published = publishGame(true, true, true, "tools-enabled");
        String deviceHash = "device-hash-tools";
        PlaySession firstSession = plays.startOrResume(published.game().getSlug(), deviceHash);

        plays.saveNotes(published.game().getSlug(), deviceHash, "clock -> red -> north");
        PlayService.ClueScanResult firstScan = plays.scanClue(
                published.game().getSlug(), deviceHash, published.item().getStableKey());
        PlayService.ClueScanResult repeatedScan = plays.scanClue(
                published.game().getSlug(), deviceHash, published.item().getStableKey());
        PlayService.PlayView resumed = plays.current(published.game().getSlug(), deviceHash);

        assertThat(firstScan.found()).isTrue();
        assertThat(firstScan.accepted()).isTrue();
        assertThat(firstScan.success()).isTrue();
        assertThat(repeatedScan.success()).isTrue();
        assertThat(resumed.notes()).isEqualTo("clock -> red -> north");
        assertThat(resumed.inventory()).extracting(ReleaseSnapshot.ItemSnapshot::stableKey)
                .containsExactly(published.item().getStableKey());
        assertThat(resumed.scannedClues()).extracting(PlayService.ScannedClueView::getStableKey)
                .containsExactly(published.item().getStableKey());
        assertThat(scannedClues.findAllByPlaySessionIdOrderByScannedAtAsc(firstSession.getId())).hasSize(1);

        PlaySession restarted = plays.restart(published.game().getSlug(), deviceHash);
        PlayService.PlayView fresh = plays.current(published.game().getSlug(), deviceHash);
        assertThat(restarted.getId()).isNotEqualTo(firstSession.getId());
        assertThat(fresh.notes()).isNull();
        assertThat(fresh.inventory()).isEmpty();
        assertThat(fresh.scannedClues()).isEmpty();
        assertThat(scannedClues.findAllByPlaySessionIdOrderByScannedAtAsc(restarted.getId())).isEmpty();
    }

    @Test
    void disabledNotebookScannerAndCluebookAreEnforcedButExternalFallbackCanStillOpenOwnedQr() {
        PublishedGame published = publishGame(false, false, false, "tools-disabled");
        String deviceHash = "device-hash-disabled";
        plays.startOrResume(published.game().getSlug(), deviceHash);

        assertThatThrownBy(() -> plays.saveNotes(published.game().getSlug(), deviceHash, "must not save"))
                .isInstanceOf(IllegalArgumentException.class);
        PlayService.ClueScanResult inAppScan = plays.scanClue(
                published.game().getSlug(), deviceHash, published.item().getStableKey());
        assertThat(inAppScan.accepted()).isFalse();
        assertThat(inAppScan.success()).isFalse();

        PlayService.ClueScanResult fallback = plays.scanClueFromLink(
                published.game().getSlug(), deviceHash, published.item().getStableKey());
        assertThat(fallback.accepted()).isTrue();
        assertThat(plays.current(published.game().getSlug(), deviceHash).inventory())
                .extracting(ReleaseSnapshot.ItemSnapshot::stableKey)
                .containsExactly(published.item().getStableKey());
        assertThat(plays.current(published.game().getSlug(), deviceHash).scannedClues()).isEmpty();
    }

    @Test
    void itemQrPayloadAcceptsOnlyTheExpectedGameAndCanonicalOrigin() {
        PublishedGame expected = publishGame(true, true, true, "payload-owner");
        PublishedGame other = publishGame(true, true, true, "payload-other");
        String payload = qrCodes.itemClueUrl(expected.game(), expected.item());

        assertThat(qrCodes.parseItemKey(payload, expected.game().getSlug()))
                .contains(expected.item().getStableKey());
        assertThat(qrCodes.parseItemKey(payload, other.game().getSlug())).isEmpty();
        assertThat(qrCodes.parseItemKey(
                "https://attacker.example/play/" + expected.game().getSlug()
                        + "/clue/" + expected.item().getStableKey(), expected.game().getSlug()))
                .isEmpty();
        assertThat(qrCodes.parseItemKey("https://escape.test/unrelated", expected.game().getSlug()))
                .isEmpty();
    }

    private PublishedGame publishGame(boolean notebook, boolean cluebook, boolean scanner, String prefix) {
        UserAccount owner = signup(prefix);
        EscapeGame game = authoring.create(owner, prefix, uniqueSlug(prefix), "QUICK_10",
                GameTheme.MIDNIGHT, Difficulty.NORMAL, 30);
        authoring.updateSettings(game.getId(), owner, game.getTitle(), game.getSlug(), "", "", null,
                "#8B5CF6", "#EC4899", "#0B1020", "🔐",
                notebook, cluebook, scanner, GameTheme.MIDNIGHT, Difficulty.NORMAL,
                30, GameVisibility.LINK_ONLY);
        GameItem item = authoring.addItem(game.getId(), owner, ItemType.EVIDENCE, "QR evidence",
                "Found by scanning", "Look underneath the clock", "🔎", true, null);
        publishing.publish(game.getId(), owner);
        return new PublishedGame(game, item);
    }

    private UserAccount signup(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return accounts.signupMaker(prefix + "+" + suffix + "@example.com",
                "password-123", "password-123", prefix);
    }

    private String uniqueSlug(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private record PublishedGame(EscapeGame game, GameItem item) {}
}
