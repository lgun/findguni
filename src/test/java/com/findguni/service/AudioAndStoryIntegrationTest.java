package com.findguni.service;

import com.findguni.model.Difficulty;
import com.findguni.model.EscapeGame;
import com.findguni.model.GameRelease;
import com.findguni.model.GameStage;
import com.findguni.model.GameTheme;
import com.findguni.model.GameVisibility;
import com.findguni.model.StoryEffect;
import com.findguni.model.UserAccount;
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
        "spring.datasource.url=jdbc:h2:mem:findguni-audio-story-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "debug=false",
        "logging.level.org.hibernate.SQL=OFF",
        "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
@Transactional
class AudioAndStoryIntegrationTest {

    @Autowired
    private AccountService accounts;

    @Autowired
    private GameAuthoringService authoring;

    @Autowired
    private PublishingService publishing;

    @Test
    void releasePreservesBgmAttributionStorySettingsAndStageSceneSfxAfterDraftChanges() {
        UserAccount owner = signup("audio-snapshot");
        EscapeGame game = createGame(owner, "audio-snapshot");
        authoring.updateSettings(game.getId(), owner, game.getTitle(), game.getSlug(), "summary", "intro", null,
                "#112233", "#445566", "#778899", "🎙️",
                true, true, true, GameTheme.MANSION, Difficulty.HARD, 70, GameVisibility.PUBLIC,
                "https://media.example.test/ambient.mp3", "Night Ambience", "Example Artist", "CC BY 4.0",
                "https://licenses.example.test/by-4.0", "https://source.example.test/night",
                0.42, false, 57, false);
        GameStage stage = authoring.stages(game.getId(), owner).get(1);
        authoring.updateStage(stage.getId(), owner, draftWithMedia(stage,
                StoryEffect.GLITCH,
                "https://media.example.test/scene.png",
                "https://media.example.test/door.ogg",
                "Door slam", "Foley Artist", "CC0",
                "https://licenses.example.test/cc0", "https://source.example.test/door", 0.73));

        GameRelease firstRelease = publishing.publish(game.getId(), owner);
        String firstJson = firstRelease.getSnapshotJson();
        ReleaseSnapshot first = publishing.readSnapshot(firstRelease);

        authoring.updateSettings(game.getId(), owner, "Changed draft", game.getSlug(), "changed", "changed", null,
                "#AABBCC", "#DDEEFF", "#010203", "🔇",
                true, true, true, GameTheme.LAB, Difficulty.EASY, 15, GameVisibility.LINK_ONLY,
                null, null, null, null, null, null,
                1.0, true, 10, true);
        authoring.updateStage(stage.getId(), owner, draftWithMedia(stage,
                StoryEffect.NONE, null, null, null, null, null, null, null, 0.1));

        ReleaseSnapshot.StageSnapshot releasedStage = first.stages().stream()
                .filter(candidate -> candidate.stableKey().equals(stage.getStableKey()))
                .findFirst()
                .orElseThrow();
        assertThat(first.bgmUrl()).isEqualTo("https://media.example.test/ambient.mp3");
        assertThat(first.bgmTitle()).isEqualTo("Night Ambience");
        assertThat(first.bgmCreator()).isEqualTo("Example Artist");
        assertThat(first.bgmLicense()).isEqualTo("CC BY 4.0");
        assertThat(first.bgmLicenseUrl()).isEqualTo("https://licenses.example.test/by-4.0");
        assertThat(first.bgmSourceUrl()).isEqualTo("https://source.example.test/night");
        assertThat(first.bgmVolume()).isEqualTo(0.42);
        assertThat(first.bgmLoop()).isFalse();
        assertThat(first.storyTextSpeed()).isEqualTo(57);
        assertThat(first.enableVignette()).isFalse();
        assertThat(releasedStage.storyEffect()).isEqualTo(StoryEffect.GLITCH);
        assertThat(releasedStage.sceneImageUrl()).isEqualTo("https://media.example.test/scene.png");
        assertThat(releasedStage.sfxUrl()).isEqualTo("https://media.example.test/door.ogg");
        assertThat(releasedStage.sfxTitle()).isEqualTo("Door slam");
        assertThat(releasedStage.sfxCreator()).isEqualTo("Foley Artist");
        assertThat(releasedStage.sfxLicense()).isEqualTo("CC0");
        assertThat(releasedStage.sfxLicenseUrl()).isEqualTo("https://licenses.example.test/cc0");
        assertThat(releasedStage.sfxSourceUrl()).isEqualTo("https://source.example.test/door");
        assertThat(releasedStage.sfxVolume()).isEqualTo(0.73);
        assertThat(firstRelease.getSnapshotJson()).isEqualTo(firstJson);
    }

    @Test
    void rejectsNonHttpsExternalAudioUrl() {
        UserAccount owner = signup("unsafe-http");
        EscapeGame game = createGame(owner, "unsafe-http");

        assertThatThrownBy(() -> updateOnlyAudio(game, owner, "http://media.example.test/file.mp3",
                "https://license.example.test", "https://source.example.test"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsExecutableAndUnsafeAttributionSchemes() {
        UserAccount owner = signup("unsafe-scheme");
        EscapeGame game = createGame(owner, "unsafe-scheme");

        assertThatThrownBy(() -> updateOnlyAudio(game, owner, "javascript:alert(1)",
                "data:text/plain,license", "file:///source"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTraversingInternalAudioPathButAcceptsStrictUuidPath() {
        UserAccount owner = signup("unsafe-path");
        EscapeGame game = createGame(owner, "unsafe-path");

        assertThatThrownBy(() -> updateOnlyAudio(game, owner, "/uploads/audio/../outside.mp3", null, null))
                .isInstanceOf(IllegalArgumentException.class);

        updateOnlyAudio(game, owner,
                "/uploads/audio/00000000-0000-0000-0000-000000000001.mp3", null, null);
        assertThat(authoring.ownedGame(game.getId(), owner).getBgmUrl())
                .isEqualTo("/uploads/audio/00000000-0000-0000-0000-000000000001.mp3");
    }

    private GameAuthoringService.StageDraft draftWithMedia(
            GameStage stage, StoryEffect effect, String sceneImageUrl, String sfxUrl,
            String sfxTitle, String sfxCreator, String sfxLicense,
            String sfxLicenseUrl, String sfxSourceUrl, double sfxVolume) {
        return new GameAuthoringService.StageDraft(
                stage.getTitle(), stage.getStory(), stage.getInstruction(), stage.getHint(),
                stage.getPuzzleType(), stage.getDraftAnswer(), stage.getOptionsText(), stage.getLockLength(),
                stage.getRequiredItem(), stage.getRewardItem(), effect, sceneImageUrl,
                sfxUrl, sfxTitle, sfxCreator, sfxLicense, sfxLicenseUrl, sfxSourceUrl, sfxVolume);
    }

    private void updateOnlyAudio(EscapeGame game, UserAccount owner, String bgmUrl,
                                 String licenseUrl, String sourceUrl) {
        authoring.updateSettings(game.getId(), owner, game.getTitle(), game.getSlug(), "", "", null,
                "#8B5CF6", "#EC4899", "#0B1020", "🔐",
                true, true, true, GameTheme.MIDNIGHT, Difficulty.NORMAL, 30, GameVisibility.LINK_ONLY,
                bgmUrl, "BGM", "Creator", "CC BY", licenseUrl, sourceUrl,
                0.5, true, 32, true);
    }

    private EscapeGame createGame(UserAccount owner, String prefix) {
        return authoring.create(owner, prefix, prefix + "-" + UUID.randomUUID(), "QUICK_10",
                GameTheme.MIDNIGHT, Difficulty.NORMAL, 30);
    }

    private UserAccount signup(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return accounts.signupMaker(prefix + "+" + suffix + "@example.com",
                "password-123", "password-123", prefix);
    }
}
