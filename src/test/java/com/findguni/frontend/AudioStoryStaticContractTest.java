package com.findguni.frontend;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AudioStoryStaticContractTest {

    @Test
    void makerBuilderExposesAllAudioStoryAndOpenverseHooksWithoutUnsafeDomSinks() throws Exception {
        String builder = resource("templates/maker/game-builder.html");
        String audioFragment = resource("templates/fragments/audio.html");
        String appJs = resource("static/js/app.js");

        assertThat(builder).contains("enctype=\"multipart/form-data\"");
        assertThat(builder).contains(
                "name=\"bgmFile\"", "name=\"removeBgm\"", "name=\"bgmVolume\"",
                "name=\"bgmLoop\"", "name=\"storyTextSpeed\"", "name=\"enableVignette\"",
                "name=\"storyEffect\"", "name=\"scenePhoto\"", "name=\"removeSceneImage\"",
                "name=\"sfxFile\"", "name=\"removeSfx\"", "name=\"sfxVolume\""
        );
        assertThat(audioFragment).contains(
                "data-audio-picker", "data-audio-kind", "data-openverse-query",
                "data-openverse-search", "data-openverse-status", "data-openverse-results",
                "data-audio-field=\"url\"", "data-audio-field=\"title\"",
                "data-audio-field=\"creator\"", "data-audio-field=\"license\"",
                "data-audio-field=\"licenseUrl\"", "data-audio-field=\"sourceUrl\""
        );
        assertNoUnsafeDynamicHtml(appJs);
    }

    @Test
    void playerStoryAndAudioScriptsKeepPreferencesRecoverAutoplayAndAvoidUnsafeHtml() throws Exception {
        String stage = resource("templates/player/stage.html");
        String audio = resource("static/js/audio.js");
        String story = resource("static/js/story.js");

        assertThat(stage).contains(
                "/js/audio.js", "/js/story.js",
                "data-story-scene", "data-story-effect", "data-story-speed", "data-vignette",
                "data-story-text", "data-audio-controller", "data-bgm-track", "data-sfx-track",
                "data-audio-toggle=\"bgm\"", "data-audio-toggle=\"sfx\"",
                "data-audio-start", "data-audio-status",
                "game.bgmLicense", "game.bgmSourceUrl", "stage.sfxLicense", "stage.sfxSourceUrl"
        );
        assertThat(audio).contains(
                "findguni.audio.preferences.v1",
                "findguni.audio.bgmPosition.",
                "findguni.audio.sfxPlayed.",
                "window.localStorage",
                "window.sessionStorage",
                "track.play()",
                ".catch(function () { return false; })",
                "showRecovery",
                "startButton.addEventListener('click'"
        );
        assertThat(story).contains(
                "prefers-reduced-motion: reduce",
                "text.textContent",
                "document.createElement('button')",
                "skip.addEventListener('click'"
        );
        assertNoUnsafeDynamicHtml(audio);
        assertNoUnsafeDynamicHtml(story);
    }

    private String resource(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }

    private void assertNoUnsafeDynamicHtml(String javascript) {
        assertThat(javascript).doesNotContain(
                ".innerHTML", "outerHTML", "insertAdjacentHTML", "document.write", "eval(", "new Function("
        );
    }
}
