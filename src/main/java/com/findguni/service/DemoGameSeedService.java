package com.findguni.service;

import com.findguni.model.*;
import com.findguni.repository.EscapeGameRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoGameSeedService {
    public static final String GUNI_SLUG = "find-guni";

    private final AccountService accounts;
    private final GameAuthoringService authoring;
    private final PublishingService publishing;
    private final EscapeGameRepository games;

    public DemoGameSeedService(AccountService accounts, GameAuthoringService authoring,
                               PublishingService publishing, EscapeGameRepository games) {
        this.accounts = accounts;
        this.authoring = authoring;
        this.publishing = publishing;
        this.games = games;
    }

    @Transactional
    public EscapeGame ensureGuniRescueDemo() {
        UserAccount maker = accounts.ensureDemoSeedAccount("demo@findguni.local", "test", "구니 구조대");
        return games.findBySlug(GUNI_SLUG).orElseGet(() -> createGuniRescueDemo(maker));
    }

    private EscapeGame createGuniRescueDemo(UserAccount maker) {
        EscapeGame game = authoring.create(maker, "구니를 찾아보자!", GUNI_SLUG,
                "BLANK", GameTheme.MANSION, Difficulty.EASY, 20, GameFlowMode.QR_EXPLORATION);
        authoring.updateSettings(game.getId(), maker, game.getTitle(), game.getSlug(),
                "집 안 곳곳의 QR 단서를 모아 베란다문에 갇힌 구니를 구출하세요.",
                "납치범의 편지가 도착했습니다. 정해진 순서는 없습니다. 집 안을 자유롭게 조사하고, "
                        + "별표가 붙은 네 개의 중요 단서를 조합해 마지막 자물쇠를 여세요.",
                "/images/유령곤란.png", "#F59E0B", "#FB7185", "#111827", "👻",
                true, true, true, GameTheme.MANSION, Difficulty.EASY, 20, GameVisibility.PUBLIC);

        GameItem timer = authoring.addItem(game.getId(), maker, ItemType.DEVICE,
                "★ 중요 단서 ① · 멈춘 오븐 타이머", "오븐 타이머가 이상한 숫자에서 멈춰 있다.",
                "첫 번째 숫자는 2. 나란히 놓인 두 개의 다이얼이 유난히 반짝인다.",
                "⏲️", true, "/images/오븐.jpg");
        GameItem cupboard = authoring.addItem(game.getId(), maker, ItemType.EVIDENCE,
                "★ 중요 단서 ② · 찬장의 그릇", "찬장 안 그릇 네 개에 같은 표식이 그려져 있다.",
                "두 번째 숫자는 4. 표식이 있는 그릇의 개수를 세어 보자.",
                "🥣", true, "/images/찬장.png");
        GameItem tree = authoring.addItem(game.getId(), maker, ItemType.SYMBOL,
                "★ 중요 단서 ③ · 철나무 장식", "기묘한 나무 장식에 붉은 표시 하나가 남아 있다.",
                "세 번째 숫자는 1. 붉게 표시된 열매만 세면 된다.",
                "🌳", true, "/images/철나무.jpg");
        GameItem moon = authoring.addItem(game.getId(), maker, ItemType.PHOTO,
                "★ 중요 단서 ④ · 보름달 사진", "사진 뒤에 ‘마지막은 하나뿐인 달’이라고 적혀 있다.",
                "네 번째 숫자는 1. 밤하늘에 떠 있는 달은 하나다.",
                "🌕", true, "/images/보름달.jpg");
        GameItem orderNote = authoring.addItem(game.getId(), maker, ItemType.DOCUMENT,
                "구니가 남긴 쪽지", "급하게 찢어 놓은 작은 메모 조각이다.",
                "별표가 붙은 중요 단서를 ① → ② → ③ → ④ 순서로 이어 붙여! 베란다문에서 기다릴게.",
                "📝", true, null);
        authoring.addItem(game.getId(), maker, ItemType.DOCUMENT,
                "낡은 배달 영수증", "숫자가 많지만 사건과는 관계없는 오래된 영수증이다.",
                "배달 날짜는 암호가 아닌 것 같다. 별표가 붙은 중요 단서에 집중하자.",
                "🧾", true, null);
        GameItem unlockedLock = authoring.addItem(game.getId(), maker, ItemType.KEY,
                "풀린 베란다 자물쇠", "네 자리 암호를 맞혀 열린 자물쇠.",
                "베란다문을 열 수 있다.", "🔓", false, null);

        GameStage letter = authoring.stages(game.getId(), maker).get(0);
        authoring.updateStage(game.getId(), letter.getId(), maker, stage(
                "납치범의 편지",
                "현관 앞에 떨어진 봉투 안에는 짧은 편지가 들어 있었다.\n\n"
                        + "‘구니는 내가 데려갔다. 찾고 싶다면 집 안 곳곳의 QR을 조사해라. "
                        + "중요한 단서 네 개를 조합하면 베란다문의 암호를 알 수 있을 것이다.’",
                "편지를 확인한 뒤 집 안에 놓인 QR 단서를 자유롭게 스캔하세요.",
                "먼저 편지를 끝까지 읽고 ‘탐색 시작’을 누르세요.",
                PuzzleType.STORY, null, 4, null, null, false,
                StoryEffect.SHAKE, "/images/유령곤란.png"), StageEntryMode.START, "");

        GameStage balcony = authoring.addStage(game.getId(), maker, stage(
                "잠긴 베란다문",
                "베란다문 손잡이에 네 자리 숫자 자물쇠가 걸려 있다. 집 안에서 모은 중요 단서를 순서대로 조합해야 한다.",
                "중요 단서 ①~④의 숫자를 이어 네 자리 자물쇠를 맞추세요.",
                "구니의 쪽지에 적힌 순서대로 읽으면 2 · 4 · 1 · 1 입니다.",
                PuzzleType.NUMBER_LOCK, "2411", 4, orderNote.getStableKey(),
                unlockedLock.getStableKey(), true, StoryEffect.SPOTLIGHT, null),
                StageEntryMode.QR, null);

        GameStage rescue = authoring.addStage(game.getId(), maker, stage(
                "구니를 구했다!",
                "철컥! 자물쇠가 풀리고 베란다문이 열린다. 상자 뒤에서 구니가 폴짝 뛰어나왔다.\n\n"
                        + "‘찾으러 와 줄 줄 알았어! 집 안의 단서를 전부 기억해 줘서 고마워.’\n\n"
                        + "구니 구출 작전 성공!",
                "구니와 함께 집으로 돌아가 게임을 완료하세요.", "구니를 찾았습니다.",
                PuzzleType.STORY, null, 4, unlockedLock.getStableKey(), null, false,
                StoryEffect.FADE, "/images/유령해피.png"), StageEntryMode.LINKED, null);
        authoring.configureStageFlow(game.getId(), balcony.getId(), maker,
                StageEntryMode.QR, rescue.getStableKey());

        publishing.publish(game.getId(), maker);
        return game;
    }

    private GameAuthoringService.StageDraft stage(
            String title, String story, String instruction, String hint,
            PuzzleType puzzleType, String answer, int lockLength,
            String requiredItem, String rewardItem, boolean qrEnabled,
            StoryEffect effect, String sceneImageUrl) {
        return new GameAuthoringService.StageDraft(title, story, instruction, hint,
                puzzleType, answer, null, lockLength, requiredItem, rewardItem,
                qrEnabled, effect, sceneImageUrl,
                null, null, null, null, null, null, null);
    }
}
