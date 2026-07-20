package com.findguni.frontend;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class QrAuthoringStaticContractTest {

    @Test
    void makerAndPlayerExposeCompressedQrFirstAuthoringAndExplorationHooks() throws Exception {
        String builder = resource("templates/maker/game-builder.html");
        String hunt = resource("templates/player/hunt.html");
        String appJs = resource("static/js/app.js");

        assertThat(builder).contains(
                "name=\"flowMode\" value=\"QR_EXPLORATION\"",
                "data-stage-workspace", "data-stage-select", "data-stage-panel",
                "class=\"advanced-editor\"", "class=\"quick-create-note\"",
                "/stages/{stageId}/qr", "name=\"qrEnabled\"", "name=\"entryMode\"",
                "name=\"nextStageKey\"",
                "QR 단서·아이템", "name=\"qrEnabled\" value=\"true\" checked"
        );
        assertThat(hunt).contains(
                "data-scanner", "data-camera-start", "data-scanner-file",
                "발견한 문제", "solvedStageKeys", "/stage/{key}"
        );
        assertThat(appJs).contains("function initStageWorkspaces()", "panel.open = panel.dataset.stageId === stageId");
        assertThat(appJs).doesNotContain(".innerHTML", "insertAdjacentHTML", "document.write");
    }

    private String resource(String path) throws Exception {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
