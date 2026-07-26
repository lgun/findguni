package com.findguni.frontend;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class QrAuthoringStaticContractTest {

    @Test
    void makerAndPlayerExposeCompressedQrFirstAuthoringAndExplorationHooks() throws Exception {
        String builder = resource("templates/maker/game-builder.html");
        String qrKit = resource("templates/maker/qr-kit.html");
        String hunt = resource("templates/player/hunt.html");
        String stage = resource("templates/player/stage.html");
        String appJs = resource("static/js/app.js");
        String playerJs = resource("static/js/player.js");
        String appCss = resource("static/css/app.css");

        assertThat(builder).contains(
                "name=\"flowMode\" value=\"QR_EXPLORATION\"",
                "data-stage-workspace", "data-stage-select", "data-stage-panel",
                "class=\"advanced-editor\"", "class=\"quick-create-note\"",
                "/stages/{stageId}/qr", "name=\"qrEnabled\"", "name=\"entryMode\"",
                "name=\"nextStageKey\"",
                "QR 단서·아이템", "name=\"qrEnabled\" value=\"true\" checked",
                "name=\"initiallyOwned\"", "name=\"copyableText\"",
                "name=\"unlimitedHints\"", "name=\"hintLimit\"", "name=\"hintCooldownSeconds\""
        );
        assertThat(builder).contains("/qr-kit", "QR 일괄 다운로드·인쇄");
        assertThat(qrKit).contains(
                "/qr-kit/print.pdf", "/qr-kit/qr-images.zip", "data-print-page",
                "class=\"qr-kit-page\"", "class=\"qr-cut-card\"", "설치 위치", "스캔 확인"
        );
        assertThat(hunt).contains(
                "data-scanner", "data-camera-start", "data-scanner-file",
                "발견한 문제", "solvedStageKeys", "/stage/{key}"
        );
        assertThat(stage).doesNotContain("data-hint-policy-status", "data-hint-wait-seconds", "hintAvailability");
        assertThat(stage).contains(
                "class=\"combination-lock\"", "combination-lock__shackle",
                "data-wheel-prev", "data-wheel-next", "class=\"door-keypad\"",
                "class=\"access-lock\"", "data-paste-answer", "lock-release__bolt"
        );
        assertThat(playerJs).contains("function initHintCooldown()", "button.disabled = false");
        assertThat(playerJs).contains(
                "pointerdown", "setPointerCapture", "navigator.vibrate",
                "navigator.clipboard.readText()", "function tactileTick"
        );
        assertThat(appCss).contains(
                ".combination-lock__shackle", ".wheel__window", ".door-keypad",
                ".access-lock__screen", "@keyframes lock-dial-click",
                ".qr-kit-page", ".qr-cut-card", "@page { size: A4 portrait"
        );
        assertThat(appJs).contains(
                "function initStageWorkspaces()", "panel.open = panel.dataset.stageId === stageId",
                "function initStageCreateEditor()", "function initItemEditors()",
                "function initPrintButtons()", "window.print()"
        );
        assertThat(appJs).doesNotContain(".innerHTML", "insertAdjacentHTML", "document.write");
        assertThat(playerJs).doesNotContain(".innerHTML", "insertAdjacentHTML", "document.write");
    }

    private String resource(String path) throws Exception {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
