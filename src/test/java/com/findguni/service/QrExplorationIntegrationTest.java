package com.findguni.service;

import com.findguni.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "findguni.seed.admin.enabled=false",
        "findguni.seed.demo.enabled=false",
        "findguni.answers.hmac-secret=test-answer-secret-with-enough-length",
        "findguni.public-base-url=https://escape.test",
        "spring.datasource.url=jdbc:h2:mem:findguni-qr-exploration-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "debug=false"
})
@Transactional
class QrExplorationIntegrationTest {

    @Autowired private AccountService accounts;
    @Autowired private GameAuthoringService authoring;
    @Autowired private PublishingService publishing;
    @Autowired private PlayService plays;
    @Autowired private QRCodeService qrCodes;

    @Test
    void qrProblemsCanBeDiscoveredAndSolvedInAnyOrderWithoutCompletingEarly() {
        UserAccount maker = accounts.signupMaker("qr+" + UUID.randomUUID() + "@example.com",
                "password-123", "password-123", "QR 메이커");
        EscapeGame game = authoring.create(maker, "비선형 QR 게임", "qr-" + UUID.randomUUID(),
                "BLANK", GameTheme.MIDNIGHT, Difficulty.NORMAL, 30, GameFlowMode.QR_EXPLORATION);
        GameStage prologue = authoring.stages(game.getId(), maker).get(0);
        GameStage lock = authoring.addStage(game.getId(), maker, new GameAuthoringService.StageDraft(
                "서재 금고", "금고를 발견했다.", "암호를 입력하세요.", "책의 연도를 보세요.",
                PuzzleType.NUMBER_LOCK, "3141", null, 4, null, null));
        publishing.publish(game.getId(), maker);

        String device = "qr-device-hash";
        plays.startOrResume(game.getSlug(), device);
        PlayService.PlayView waiting = plays.current(game.getSlug(), device);
        assertThat(waiting.game().flowMode()).isEqualTo(GameFlowMode.QR_EXPLORATION);
        assertThat(waiting.stage()).isNull();
        assertThat(waiting.discoveredStages()).isEmpty();
        assertThat(plays.selectDiscoveredStage(game.getSlug(), device, lock.getStableKey())).isFalse();

        PlayService.QrScanResult foundLock = plays.scanStage(game.getSlug(), device, lock.getStableKey(), true);
        assertThat(foundLock.accepted()).isTrue();
        assertThat(plays.current(game.getSlug(), device).stage().stableKey()).isEqualTo(lock.getStableKey());
        assertThat(plays.solve(game.getSlug(), device, "0000").success()).isFalse();
        PlayService.SolveResult lockSolved = plays.solve(game.getSlug(), device, "31-41");
        assertThat(lockSolved.success()).isTrue();
        assertThat(lockSolved.completed()).isFalse();
        assertThat(plays.current(game.getSlug(), device).stage()).isNull();
        assertThat(plays.current(game.getSlug(), device).solvedStageKeys()).containsExactlyInAnyOrder(lock.getStableKey());

        assertThat(plays.scanStage(game.getSlug(), device, prologue.getStableKey(), true).accepted()).isTrue();
        PlayService.SolveResult completed = plays.solve(game.getSlug(), device, "");
        assertThat(completed.completed()).isTrue();
        assertThat(plays.current(game.getSlug(), device).session().getStatus()).isEqualTo(PlayStatus.COMPLETED);
    }

    @Test
    void stageQrPayloadIsStrictlyParsedAsAStageForTheExpectedGame() {
        UserAccount maker = accounts.signupMaker("qr-parse+" + UUID.randomUUID() + "@example.com",
                "password-123", "password-123", "QR 메이커");
        EscapeGame game = authoring.create(maker, "QR 파서", "qr-parser-" + UUID.randomUUID(),
                "BLANK", GameTheme.LAB, Difficulty.EASY, 20, GameFlowMode.QR_EXPLORATION);
        GameStage stage = authoring.stages(game.getId(), maker).get(0);

        QRCodeService.QrTarget target = qrCodes.parseTarget(qrCodes.stagePuzzleUrl(game, stage), game.getSlug())
                .orElseThrow();
        assertThat(target.type()).isEqualTo(QRCodeService.QrTargetType.STAGE);
        assertThat(target.stableKey()).isEqualTo(stage.getStableKey());
        assertThat(qrCodes.parseTarget(qrCodes.stagePuzzleUrl(game, stage), "another-game")).isEmpty();
        assertThat(qrCodes.parseTarget("https://evil.example/play/" + game.getSlug()
                + "/puzzle/" + stage.getStableKey(), game.getSlug())).isEmpty();
    }
}
