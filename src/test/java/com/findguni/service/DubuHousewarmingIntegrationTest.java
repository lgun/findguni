package com.findguni.service;

import com.findguni.model.EscapeGame;
import com.findguni.model.GameStage;
import com.findguni.model.Difficulty;
import com.findguni.model.PlayStatus;
import com.findguni.model.PuzzleType;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "findguni.seed.admin.enabled=false",
        "findguni.seed.demo.enabled=false",
        "findguni.answers.hmac-secret=test-answer-secret-with-enough-length",
        "findguni.public-base-url=https://escape.test",
        "spring.datasource.url=jdbc:h2:mem:findguni-dubu-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "debug=false"
})
@Transactional
class DubuHousewarmingIntegrationTest {

    @Autowired private DubuHousewarmingSeedService dubuGames;
    @Autowired private GameAuthoringService authoring;
    @Autowired private PlayService plays;
    @Autowired private QrPrintKitService qrPrintKits;

    @Test
    void refreshesAnOlderSeededDraftBeforeReturningIt() {
        EscapeGame game = dubuGames.ensureDubuHousewarming();
        GameStage revisionStage = authoring.stages(game.getId(), game.getOwner()).stream()
                .filter(stage -> stage.getTitle().equals("두부네 현관: 빈 간식 접시"))
                .findFirst().orElseThrow();

        authoring.deleteStage(game.getId(), revisionStage.getId(), game.getOwner());
        EscapeGame refreshed = dubuGames.ensureDubuHousewarming();

        assertThat(refreshed.getId()).isEqualTo(game.getId());
        assertThat(authoring.stages(game.getId(), game.getOwner()))
                .hasSize(15)
                .extracting(GameStage::getTitle)
                .contains("큰집사에게 들켰다!", "놀이방: 임시 케이지를 부숴라", "자택경비원 등 뒤",
                        "놀이방: 베놈에게 잡힌 인형", "화장실 2: 물병 분실함",
                        "거실: 울고 있는 얼굴 꽃병", "화장실 1: 목마른 코크베어",
                        "조합소: 두부 선물 만들기", "두부네 현관: 빈 간식 접시",
                        "침실: 쿠기의 예비 간식함", "두부네 앞: 옛날쿠키의 정령");
    }

    @Test
    void fullHousewarmingRouteStartsWithInvitationAndEndsWithKukiPassword() {
        EscapeGame game = dubuGames.ensureDubuHousewarming();
        String device = "dubu-housewarming-device";

        assertThat(game.getDifficulty()).isEqualTo(Difficulty.NORMAL);
        assertThat(game.isUnlimitedHints()).isTrue();
        assertThat(game.getHintCooldownSeconds()).isZero();

        plays.startOrResume(game.getSlug(), device);
        PlayService.PlayView opening = plays.current(game.getSlug(), device);
        assertThat(opening.stage().title()).isEqualTo("현관: 두부의 엉터리 초대장");
        assertThat(opening.inventory()).filteredOn(item -> item.name().equals("두부의 집들이 초대장"))
                .singleElement().satisfies(invitation -> {
            assertThat(invitation.name()).isEqualTo("두부의 집들이 초대장");
            assertThat(invitation.initiallyOwned()).isTrue();
            assertThat(invitation.imageUrl()).isEqualTo("/images/dubu/dubu-bad-map.png");
        });
        assertThat(opening.inventory()).extracting(ReleaseSnapshot.ItemSnapshot::name)
                .contains("햄스터어 회화 카드");
        assertThat(opening.game().stages())
                .filteredOn(stage -> !stage.title().equals("두부네 도어락"))
                .extracting(ReleaseSnapshot.StageSnapshot::puzzleType)
                .doesNotContain(PuzzleType.MULTIPLE_CHOICE, PuzzleType.TEXT_ANSWER);
        assertThat(opening.game().stages()).filteredOn(stage -> stage.puzzleType() == PuzzleType.DIRECTION_LOCK)
                .hasSize(1);
        assertThat(opening.game().stages()).filteredOn(stage -> stage.puzzleType() == PuzzleType.KEYPAD)
                .hasSize(1);

        ReleaseSnapshot.ItemSnapshot funQr = opening.game().items().stream()
                .filter(item -> item.name().equals("잠든 철갑거북"))
                .findFirst().orElseThrow();
        PlayService.ClueScanResult funScan = plays.scanClue(game.getSlug(), device, funQr.stableKey());
        assertThat(funScan.accepted()).isTrue();
        assertThat(funScan.success()).isTrue();
        assertThat(plays.current(game.getSlug(), device).stage().title()).isEqualTo("현관: 두부의 엉터리 초대장");
        assertThat(plays.current(game.getSlug(), device).inventory())
                .extracting(ReleaseSnapshot.ItemSnapshot::name)
                .contains("잠든 철갑거북");

        assertThat(plays.solve(game.getSlug(), device, "").success()).isTrue();
        solveQr(game, device, "냉장고: 사람 음식뿐", "");

        PlayService.PlayView caught = plays.current(game.getSlug(), device);
        assertThat(caught.stage().title()).isEqualTo("큰집사에게 들켰다!");
        assertThat(plays.solve(game.getSlug(), device, "").success()).isTrue();

        solveQr(game, device, "놀이방: 임시 케이지를 부숴라", "");
        scanItemQr(game, device, "밤톨 경비원의 체포 딱지");
        assertThat(plays.current(game.getSlug(), device).inventory())
                .extracting(ReleaseSnapshot.ItemSnapshot::name)
                .doesNotContain("밤톨 경비원의 장난감 칼");
        solveQr(game, device, "자택경비원 등 뒤", "");
        ReleaseSnapshot.ItemSnapshot guardFront = plays.current(game.getSlug(), device).game().items().stream()
                .filter(candidate -> candidate.name().equals("밤톨 경비원의 체포 딱지"))
                .findFirst().orElseThrow();
        PlayService.ClueScanResult secretRelease = plays.scanClue(
                game.getSlug(), device, guardFront.stableKey());
        assertThat(secretRelease.success()).isTrue();
        assertThat(secretRelease.message()).contains("내 칼이 없잖아", "쉿, 그냥 가");
        assertThat(plays.current(game.getSlug(), device).inventory())
                .extracting(ReleaseSnapshot.ItemSnapshot::name)
                .contains("밤톨 경비원의 장난감 칼");
        solveQr(game, device, "놀이방: 베놈에게 잡힌 인형", "");
        solveQr(game, device, "화장실 2: 물병 분실함", "RIGHT,DOWN,LEFT,UP");
        solveQr(game, device, "거실: 울고 있는 얼굴 꽃병", "");
        solveQr(game, device, "화장실 1: 목마른 코크베어", "");
        scanItemQr(game, device, "소파 밑 휴지심");

        PlayService.PlayView beforeCombination = plays.current(game.getSlug(), device);
        assertThat(beforeCombination.inventory()).extracting(ReleaseSnapshot.ItemSnapshot::name)
                .contains("간식 ① 집사의 해바라기씨", "간식 ② 인형의 건조 채소",
                        "간식 ③ 코크베어의 곡물 큐브", "소파 밑 휴지심", "민트색 포장용 끈");
        solveQr(game, device, "조합소: 두부 선물 만들기", "");
        assertThat(plays.current(game.getSlug(), device).inventory())
                .extracting(ReleaseSnapshot.ItemSnapshot::name)
                .contains("두부의 간식선물세트")
                .doesNotContain("간식 ① 집사의 해바라기씨", "간식 ② 인형의 건조 채소",
                        "간식 ③ 코크베어의 곡물 큐브", "소파 밑 휴지심", "민트색 포장용 끈");

        scanItemQr(game, device, "소파 밑 휴지심");
        assertThat(plays.current(game.getSlug(), device).inventory())
                .extracting(ReleaseSnapshot.ItemSnapshot::name)
                .doesNotContain("소파 밑 휴지심");

        solveQr(game, device, "두부네 현관: 빈 간식 접시", "");
        assertThat(plays.current(game.getSlug(), device).inventory())
                .filteredOn(item -> item.name().equals("쿠기의 예비 간식함 표찰"))
                .singleElement()
                .satisfies(item -> assertThat(item.clueText())
                        .contains("큰집사 침대 아래", "민트 → 노랑 → 분홍 → 파랑"));
        solveQr(game, device, "침실: 쿠기의 예비 간식함", DubuHousewarmingSeedService.FLAX_BOX_PASSWORD);
        assertThat(plays.current(game.getSlug(), device).inventory())
                .filteredOn(item -> item.name().equals("쿠기의 아마씨 봉투"))
                .singleElement()
                .satisfies(item -> assertThat(item.clueText())
                        .contains("쿠기 / 아마씨", "팔찌 옆 작은 간식 접시"));
        solveQr(game, device, "두부네 앞: 옛날쿠키의 정령", "");

        PlayService.PlayView beforeDoor = plays.current(game.getSlug(), device);
        assertThat(beforeDoor.inventory()).filteredOn(item -> item.name().equals("옛날쿠키의 도어락 비밀번호"))
                .singleElement().satisfies(item -> {
                    assertThat(item.copyableText()).isEqualTo(DubuHousewarmingSeedService.DOOR_PASSWORD);
                    assertThat(item.qrEnabled()).isFalse();
                });

        solveQr(game, device, "두부네 도어락", DubuHousewarmingSeedService.DOOR_PASSWORD);
        PlayService.PlayView ending = plays.current(game.getSlug(), device);
        assertThat(ending.stage().title()).isEqualTo("두부의 집들이");
        assertThat(ending.stage().puzzleType()).isEqualTo(PuzzleType.STORY);

        PlayService.SolveResult completed = plays.solve(game.getSlug(), device, "");
        assertThat(completed.success()).isTrue();
        assertThat(completed.completed()).isTrue();
        assertThat(plays.current(game.getSlug(), device).session().getStatus()).isEqualTo(PlayStatus.COMPLETED);
    }

    @Test
    void housewarmingQrKitCollectsCoreAndFunCodesIntoFiveA4Pages() throws Exception {
        EscapeGame game = dubuGames.ensureDubuHousewarming();
        QrPrintKitService.QrKit kit = qrPrintKits.build(game,
                authoring.stages(game.getId(), game.getOwner()),
                authoring.items(game.getId(), game.getOwner()));

        assertThat(kit.stageCount()).isEqualTo(12);
        assertThat(kit.itemCount()).isEqualTo(12);
        assertThat(kit.totalCount()).isEqualTo(25);
        assertThat(kit.pageCount()).isEqualTo(5);
        assertThat(kit.cards()).extracting(QrPrintKitService.QrCard::title)
                .contains("연두부, 두부네 집들이 가는 중!", "자택경비원 등 뒤",
                        "놀이방: 베놈에게 잡힌 인형", "화장실 2: 물병 분실함",
                        "조합소: 두부 선물 만들기", "두부네 현관: 빈 간식 접시",
                        "침실: 쿠기의 예비 간식함", "소파 밑 휴지심",
                        "밤톨 경비원의 체포 딱지", "두부네 도어락", "잠든 철갑거북");
        try (PDDocument document = Loader.loadPDF(qrPrintKits.pdf(kit))) {
            assertThat(document.getNumberOfPages()).isEqualTo(5);
        }
    }

    private void solveQr(EscapeGame game, String device, String title, String answer) {
        ReleaseSnapshot.StageSnapshot stage = plays.current(game.getSlug(), device).game().stages().stream()
                .filter(candidate -> candidate.title().equals(title))
                .findFirst().orElseThrow();
        assertThat(plays.scanStage(game.getSlug(), device, stage.stableKey(), true).accepted()).isTrue();
        assertThat(plays.solve(game.getSlug(), device, answer).success())
                .as("%s should accept its configured answer", title).isTrue();
    }

    private void scanItemQr(EscapeGame game, String device, String name) {
        ReleaseSnapshot.ItemSnapshot item = plays.current(game.getSlug(), device).game().items().stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst().orElseThrow();
        assertThat(item.qrEnabled()).isTrue();
        assertThat(plays.scanClue(game.getSlug(), device, item.stableKey()).success())
                .as("%s should be collected from its item QR", name).isTrue();
    }
}
