package com.findguni.frontend;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MakerThemePreviewStaticContractTest {

    @Test
    void makerCanPreviewThemesCustomColorsAndBothPlayerStatesBeforeSaving() throws Exception {
        String builder = resource("templates/maker/game-builder.html");
        String appJs = resource("static/js/app.js");

        assertThat(builder).contains(
                "class=\"theme-studio\"",
                "data-theme-preview",
                "data-theme-palette-apply",
                "data-preview-mode=\"landing\"",
                "data-preview-mode=\"stage\"",
                "data-preview-panel=\"landing\"",
                "data-preview-panel=\"stage\"",
                "data-preview-contrast",
                "data-preview-option=\"preview-game-difficulty\"",
                "data-preview-value=\"preview-game-minutes\""
        );
        assertThat(count(builder, "data-theme-choice=")).isEqualTo(5);
        assertThat(count(builder, "data-color-pair")).isEqualTo(3);
        assertThat(appJs).contains(
                "function initThemePreview()",
                "function updatePreviewContrast()",
                "function initPreviewValues()",
                "function initPreviewModes()",
                "textContent",
                "setProperty(cssVariable, safeValue)"
        );
        assertThat(appJs).doesNotContain(
                ".innerHTML", "outerHTML", "insertAdjacentHTML", "document.write", "eval(", "new Function("
        );
    }

    private String resource(String path) throws Exception {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }

    private int count(String value, String token) {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }
}
