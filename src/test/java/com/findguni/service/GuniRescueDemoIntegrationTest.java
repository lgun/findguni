package com.findguni.service;

import com.findguni.model.EscapeGame;
import com.findguni.model.GameItem;
import com.findguni.model.GameStage;
import com.findguni.model.GameStatus;
import com.findguni.model.PlayStatus;
import com.findguni.model.StageEntryMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "findguni.seed.admin.enabled=false",
        "findguni.seed.demo.enabled=false",
        "findguni.answers.hmac-secret=test-answer-secret-with-enough-length",
        "findguni.public-base-url=https://escape.test",
        "spring.datasource.url=jdbc:h2:mem:findguni-guni-demo-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "debug=false"
})
@Transactional
class GuniRescueDemoIntegrationTest {

    @Autowired private DemoGameSeedService demoGames;
    @Autowired private GameAuthoringService authoring;
    @Autowired private PlayService plays;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void seededQrCluesUnlockBalconyAndOpenTheLinkedRescueScene() {
        EscapeGame game = demoGames.ensureGuniRescueDemo();
        assertThat(game.getSlug()).isEqualTo("find-guni");
        assertThat(game.getStatus()).isEqualTo(GameStatus.PUBLISHED);
        assertThat(passwordEncoder.matches("test", game.getOwner().getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("Demo1234!", game.getOwner().getPasswordHash())).isFalse();
        assertThat(demoGames.ensureGuniRescueDemo().getId()).isEqualTo(game.getId());

        List<GameStage> stages = authoring.stages(game.getId(), game.getOwner());
        List<GameItem> items = authoring.items(game.getId(), game.getOwner());
        GameStage letter = stage(stages, "납치범의 편지");
        GameStage balcony = stage(stages, "잠긴 베란다문");
        GameStage rescue = stage(stages, "구니를 구했다!");
        assertThat(letter.getEntryMode()).isEqualTo(StageEntryMode.START);
        assertThat(balcony.getEntryMode()).isEqualTo(StageEntryMode.QR);
        assertThat(rescue.getEntryMode()).isEqualTo(StageEntryMode.LINKED);
        assertThat(balcony.getNextStageKey()).isEqualTo(rescue.getStableKey());
        assertThat(items).filteredOn(GameItem::isQrEnabled).hasSize(6);

        String device = "guni-demo-device";
        plays.startOrResume(game.getSlug(), device);
        assertThat(plays.current(game.getSlug(), device).stage().stableKey()).isEqualTo(letter.getStableKey());
        assertThat(plays.solve(game.getSlug(), device, "").success()).isTrue();
        assertThat(plays.current(game.getSlug(), device).stage()).isNull();

        assertThat(plays.scanStage(game.getSlug(), device, balcony.getStableKey(), true).accepted()).isTrue();
        assertThat(plays.solve(game.getSlug(), device, "2411").success()).isFalse();

        for (GameItem clue : items.stream().filter(GameItem::isQrEnabled).toList()) {
            assertThat(plays.scanClue(game.getSlug(), device, clue.getStableKey()).accepted()).isTrue();
        }
        assertThat(plays.scanStage(game.getSlug(), device, balcony.getStableKey(), true).accepted()).isTrue();
        PlayService.SolveResult opened = plays.solve(game.getSlug(), device, "2-4-1-1");
        assertThat(opened.success()).isTrue();
        assertThat(opened.completed()).isFalse();
        assertThat(opened.message()).isEqualTo("다음 장면이 열렸습니다.");
        assertThat(plays.current(game.getSlug(), device).stage().stableKey()).isEqualTo(rescue.getStableKey());

        String shortcutDevice = "guni-shortcut-device";
        plays.startOrResume(game.getSlug(), shortcutDevice);
        plays.solve(game.getSlug(), shortcutDevice, "");
        assertThat(plays.scanStage(game.getSlug(), shortcutDevice, rescue.getStableKey(), true).accepted()).isFalse();

        PlayService.SolveResult completed = plays.solve(game.getSlug(), device, "");
        assertThat(completed.completed()).isTrue();
        assertThat(plays.current(game.getSlug(), device).session().getStatus()).isEqualTo(PlayStatus.COMPLETED);
        assertThat(plays.current(game.getSlug(), device).scannedClues()).hasSize(6);
    }

    private GameStage stage(List<GameStage> stages, String title) {
        return stages.stream().filter(candidate -> candidate.getTitle().equals(title)).findFirst().orElseThrow();
    }
}
