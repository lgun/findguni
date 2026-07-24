package com.findguni.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.findguni.model.Difficulty;
import com.findguni.model.EscapeGame;
import com.findguni.model.GameItem;
import com.findguni.model.GameFlowMode;
import com.findguni.model.GameRelease;
import com.findguni.model.GameStage;
import com.findguni.model.GameTheme;
import com.findguni.model.GameVisibility;
import com.findguni.model.ItemType;
import com.findguni.model.PuzzleType;
import com.findguni.model.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "findguni.seed.admin.enabled=false",
        "findguni.seed.demo.enabled=false",
        "findguni.answers.hmac-secret=test-answer-secret-with-enough-length",
        "spring.datasource.url=jdbc:h2:mem:findguni-template-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "debug=false",
        "logging.level.org.hibernate.SQL=OFF",
        "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
@Transactional
class TemplateAndSnapshotIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private static final List<String> TEMPLATE_IDS = List.of(
            "BLANK", "QUICK_10", "MYSTERY_MANSION", "DETECTIVE_CASE",
            "HORROR_HOSPITAL", "TREASURE_HUNT", "SCHOOL_MISSION", "MUSEUM_TOUR",
            "SCI_FI_LAB", "FANTASY_QUEST", "OUTDOOR_TRAIL", "FESTIVAL_EVENT",
            "KIDS_ADVENTURE", "TEAM_RACE"
    );

    @Autowired
    private AccountService accounts;

    @Autowired
    private GameAuthoringService authoring;

    @Autowired
    private PublishingService publishing;

    @Test
    void everyMakerTemplateCreatesAPublishableMinimumGameAndTogetherCoverMajorPuzzleTypes() {
        UserAccount owner = signup("template-owner");
        Set<PuzzleType> observedTypes = EnumSet.noneOf(PuzzleType.class);

        for (String templateId : TEMPLATE_IDS) {
            EscapeGame game = authoring.create(owner, templateId, uniqueSlug(templateId), templateId,
                    GameTheme.MIDNIGHT, Difficulty.NORMAL, 30);
            List<GameStage> draftStages = authoring.stages(game.getId(), owner);

            assertThat(draftStages)
                    .as("%s must create at least one stage", templateId)
                    .isNotEmpty();
            assertThat(draftStages)
                    .as("%s answer-requiring stages need a usable draft answer", templateId)
                    .filteredOn(stage -> stage.getPuzzleType().requiresAnswer())
                    .allSatisfy(stage -> assertThat(stage.getDraftAnswer()).isNotBlank());

            GameRelease release = publishing.publish(game.getId(), owner);
            ReleaseSnapshot snapshot = publishing.readSnapshot(release);
            assertThat(snapshot.stages()).hasSameSizeAs(draftStages);
            observedTypes.addAll(snapshot.stages().stream()
                    .map(ReleaseSnapshot.StageSnapshot::puzzleType)
                    .toList());
        }

        assertThat(observedTypes).contains(
                PuzzleType.NUMBER_LOCK,
                PuzzleType.DIRECTION_LOCK,
                PuzzleType.ALPHABET_LOCK,
                PuzzleType.COLOR_LOCK,
                PuzzleType.KEYPAD,
                PuzzleType.MULTIPLE_CHOICE,
                PuzzleType.TEXT_ANSWER
        );
    }

    @Test
    void customThemeToolFlagsAndRichItemFieldsRemainImmutableInPublishedSnapshot() throws Exception {
        UserAccount owner = signup("snapshot-owner");
        EscapeGame game = authoring.create(owner, "Snapshot", uniqueSlug("snapshot"), "QUICK_10",
                GameTheme.MIDNIGHT, Difficulty.NORMAL, 30);
        String imageUrl = "/uploads/00000000-0000-0000-0000-000000000001.png";

        authoring.updateSettings(game.getId(), owner, "Snapshot", game.getSlug(), "summary", "intro", null,
                "#112233", "#445566", "#778899", "🧭",
                false, true, false, GameTheme.MANSION, Difficulty.HARD, 75, GameVisibility.PUBLIC);
        authoring.updateHintPolicy(game.getId(), owner, false, 4, 20);
        GameItem item = authoring.addItem(game.getId(), owner, ItemType.MAP, "Archive map",
                "A map with a torn corner", "The red line begins at the clock", "🗺️", true, imageUrl);

        GameRelease firstRelease = publishing.publish(game.getId(), owner);
        String firstJson = firstRelease.getSnapshotJson();
        ReleaseSnapshot first = publishing.readSnapshot(firstRelease);
        ObjectNode legacyJson = (ObjectNode) objectMapper.readTree(firstJson);
        legacyJson.remove(List.of("unlimitedHints", "hintLimit", "hintCooldownSeconds"));
        ReleaseSnapshot legacySnapshot = objectMapper.treeToValue(legacyJson, ReleaseSnapshot.class);

        authoring.updateSettings(game.getId(), owner, "Changed draft", game.getSlug(), "changed", "changed", null,
                "#AABBCC", "#DDEEFF", "#010203", "🔒",
                true, false, true, GameTheme.LAB, Difficulty.EASY, 15, GameVisibility.LINK_ONLY);
        authoring.updateHintPolicy(game.getId(), owner, true, 9, 0);
        authoring.updateItem(game.getId(), item.getId(), owner, ItemType.EVIDENCE, "Changed item",
                "changed", "changed", "🔍", false, null);

        ReleaseSnapshot.ItemSnapshot publishedItem = first.items().stream()
                .filter(candidate -> candidate.stableKey().equals(item.getStableKey()))
                .findFirst()
                .orElseThrow();
        assertThat(first.accentColor()).isEqualTo("#112233");
        assertThat(first.secondaryColor()).isEqualTo("#445566");
        assertThat(first.backgroundColor()).isEqualTo("#778899");
        assertThat(first.gameIcon()).isEqualTo("🧭");
        assertThat(first.allowNotebook()).isFalse();
        assertThat(first.allowCluebook()).isTrue();
        assertThat(first.allowQrScanner()).isFalse();
        assertThat(first.isUnlimitedHints()).isFalse();
        assertThat(first.getHintLimit()).isEqualTo(4);
        assertThat(first.getHintCooldownSeconds()).isEqualTo(20);
        assertThat(legacySnapshot.isUnlimitedHints()).isTrue();
        assertThat(legacySnapshot.getHintLimit()).isEqualTo(3);
        assertThat(legacySnapshot.getHintCooldownSeconds()).isZero();
        assertThat(publishedItem.itemType()).isEqualTo(ItemType.MAP);
        assertThat(publishedItem.name()).isEqualTo("Archive map");
        assertThat(publishedItem.description()).isEqualTo("A map with a torn corner");
        assertThat(publishedItem.clueText()).isEqualTo("The red line begins at the clock");
        assertThat(publishedItem.emoji()).isEqualTo("🗺️");
        assertThat(publishedItem.qrEnabled()).isTrue();
        assertThat(publishedItem.imageUrl()).isEqualTo(imageUrl);
        assertThat(firstRelease.getSnapshotJson()).isEqualTo(firstJson);
    }

    @Test
    void qrGameCannotPublishWhenItemRewardsFormAnUnsolvableCycle() {
        UserAccount owner = signup("cycle-owner");
        EscapeGame game = authoring.create(owner, "Cycle", uniqueSlug("cycle"), "BLANK",
                GameTheme.MIDNIGHT, Difficulty.NORMAL, 30, GameFlowMode.QR_EXPLORATION);
        GameItem firstKey = authoring.addItem(game.getId(), owner, ItemType.KEY, "첫 열쇠",
                "", "", "1️⃣", false, null);
        GameItem secondKey = authoring.addItem(game.getId(), owner, ItemType.KEY, "둘째 열쇠",
                "", "", "2️⃣", false, null);

        authoring.addStage(game.getId(), owner, new GameAuthoringService.StageDraft(
                "첫 잠금", "", "", "", PuzzleType.STORY, "", "", 4,
                secondKey.getStableKey(), firstKey.getStableKey()));
        authoring.addStage(game.getId(), owner, new GameAuthoringService.StageDraft(
                "둘째 잠금", "", "", "", PuzzleType.STORY, "", "", 4,
                firstKey.getStableKey(), secondKey.getStableKey()));

        assertThatThrownBy(() -> publishing.publish(game.getId(), owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("필요한 아이템을 먼저 얻을 방법이 없습니다");
    }

    private UserAccount signup(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return accounts.signupMaker(prefix + "+" + suffix + "@example.com",
                "password-123", "password-123", prefix);
    }

    private String uniqueSlug(String prefix) {
        return prefix.toLowerCase().replace('_', '-') + "-" + UUID.randomUUID();
    }
}
