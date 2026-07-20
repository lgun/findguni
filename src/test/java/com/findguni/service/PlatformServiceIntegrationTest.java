package com.findguni.service;

import com.findguni.model.Difficulty;
import com.findguni.model.EscapeGame;
import com.findguni.model.GameRelease;
import com.findguni.model.GameStage;
import com.findguni.model.GameTheme;
import com.findguni.model.PlaySession;
import com.findguni.model.PlayStatus;
import com.findguni.model.PuzzleType;
import com.findguni.model.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "findguni.seed.admin.enabled=false",
        "findguni.seed.demo.enabled=false",
        "findguni.answers.hmac-secret=test-answer-secret-with-enough-length",
        "spring.datasource.url=jdbc:h2:mem:findguni-service-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "debug=false",
        "logging.level.org.hibernate.SQL=OFF",
        "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
@Transactional
class PlatformServiceIntegrationTest {

    @Autowired
    private AccountService accounts;

    @Autowired
    private GameAuthoringService authoring;

    @Autowired
    private PublishingService publishing;

    @Autowired
    private PlayService plays;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void makerSignupNormalizesEmailAndStoresOnlyPasswordHash() {
        UserAccount maker = accounts.signupMaker(
                "  MAKER@example.com ", "password-123", "password-123", "첫 메이커");

        assertThat(maker.getEmail()).isEqualTo("maker@example.com");
        assertThat(maker.getPasswordHash()).isNotEqualTo("password-123");
        assertThat(passwordEncoder.matches("password-123", maker.getPasswordHash())).isTrue();
        assertThat(userDetailsService.loadUserByUsername("maker@example.com").getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_MAKER");
    }

    @Test
    void anotherMakerCannotReadOrEditOwnedGame() {
        UserAccount owner = signup("owner");
        UserAccount intruder = signup("intruder");
        EscapeGame game = createQuickGame(owner, "owner-game");

        assertThatThrownBy(() -> authoring.ownedGame(game.getId(), intruder))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> authoring.updateSettings(
                game.getId(), intruder, "탈취 시도", "stolen", "", "", null, null,
                GameTheme.LAB, Difficulty.HARD, 60, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void publishingCreatesImmutableVersionedSnapshotWithoutPlainAnswer() {
        UserAccount owner = signup("publisher");
        EscapeGame game = createQuickGame(owner, "snapshot-game");

        GameRelease first = publishing.publish(game.getId(), owner);
        String firstJson = first.getSnapshotJson();
        ReleaseSnapshot firstSnapshot = publishing.readSnapshot(first);

        GameStage lock = authoring.stages(game.getId(), owner).stream()
                .filter(stage -> stage.getPuzzleType() == PuzzleType.NUMBER_LOCK)
                .findFirst()
                .orElseThrow();
        authoring.updateStage(lock.getId(), owner, new GameAuthoringService.StageDraft(
                "바뀐 자물쇠", "새 이야기", "새 암호를 입력하세요.", "", PuzzleType.NUMBER_LOCK,
                "9876", null, 4, null, null));

        GameRelease second = publishing.publish(game.getId(), owner);

        assertThat(first.getVersionNumber()).isEqualTo(1);
        assertThat(second.getVersionNumber()).isEqualTo(2);
        assertThat(first.getSnapshotJson()).isEqualTo(firstJson);
        assertThat(firstJson).doesNotContain("1234");
        assertThat(firstSnapshot.stages()).extracting(ReleaseSnapshot.StageSnapshot::title)
                .doesNotContain("바뀐 자물쇠");
        assertThat(publishing.readSnapshot(second).stages())
                .extracting(ReleaseSnapshot.StageSnapshot::title)
                .contains("바뀐 자물쇠");
    }

    @Test
    void anonymousDeviceResumesSameReleaseAndAdvancesOnlyOnCorrectAnswer() {
        UserAccount owner = signup("play-owner");
        EscapeGame game = createQuickGame(owner, "resume-game");
        GameRelease release = publishing.publish(game.getId(), owner);

        PlaySession started = plays.startOrResume(game.getSlug(), "device-hash-a");
        PlaySession resumed = plays.startOrResume(game.getSlug(), "device-hash-a");
        PlaySession anotherDevice = plays.startOrResume(game.getSlug(), "device-hash-b");

        assertThat(resumed.getId()).isEqualTo(started.getId());
        assertThat(resumed.getRelease().getId()).isEqualTo(release.getId());
        assertThat(anotherDevice.getId()).isNotEqualTo(started.getId());
        assertThat(plays.current(game.getSlug(), "device-hash-a").stage().puzzleType())
                .isEqualTo(PuzzleType.STORY);

        assertThat(plays.solve(game.getSlug(), "device-hash-a", "").success()).isTrue();
        assertThat(plays.current(game.getSlug(), "device-hash-a").stage().puzzleType())
                .isEqualTo(PuzzleType.NUMBER_LOCK);
        assertThat(plays.solve(game.getSlug(), "device-hash-a", "0000").success()).isFalse();
        assertThat(plays.current(game.getSlug(), "device-hash-a").session().getProgressIndex()).isEqualTo(1);
        assertThat(plays.solve(game.getSlug(), "device-hash-a", "12-34").success()).isTrue();
        assertThat(plays.solve(game.getSlug(), "device-hash-a", "").completed()).isTrue();
        assertThat(plays.current(game.getSlug(), "device-hash-a").session().getStatus())
                .isEqualTo(PlayStatus.COMPLETED);
    }

    private UserAccount signup(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return accounts.signupMaker(prefix + "+" + suffix + "@example.com",
                "password-123", "password-123", prefix);
    }

    private EscapeGame createQuickGame(UserAccount owner, String slugPrefix) {
        return authoring.create(owner, "테스트 방탈출", slugPrefix + "-" + UUID.randomUUID(),
                "QUICK", GameTheme.MIDNIGHT, Difficulty.NORMAL, 30);
    }
}
