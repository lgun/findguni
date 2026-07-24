package com.findguni.service;

import com.findguni.model.*;
import com.findguni.repository.EscapeGameRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DubuHousewarmingSeedService {
    public static final String SLUG = "dubu-housewarming";
    public static final String DOOR_PASSWORD = "gfhgjreohej490hgdfg23w34";
    public static final String FLAX_BOX_PASSWORD = "4904";
    private static final String CURRENT_REVISION_STAGE = "두부네 현관: 빈 간식 접시";

    private final AccountService accounts;
    private final GameAuthoringService authoring;
    private final PublishingService publishing;
    private final EscapeGameRepository games;

    public DubuHousewarmingSeedService(AccountService accounts, GameAuthoringService authoring,
                                       PublishingService publishing, EscapeGameRepository games) {
        this.accounts = accounts;
        this.authoring = authoring;
        this.publishing = publishing;
        this.games = games;
    }

    @Transactional
    public EscapeGame ensureDubuHousewarming() {
        UserAccount maker = accounts.ensureDemoSeedAccount("demo@findguni.local", "test", "Findguni 메이커");
        return games.findBySlug(SLUG)
                .map(game -> refreshOldSeedIfNeeded(game, maker))
                .orElseGet(() -> create(maker));
    }

    private EscapeGame create(UserAccount maker) {
        EscapeGame game = authoring.create(maker, "연두부, 두부네 집들이 가는 중!", SLUG,
                "BLANK", GameTheme.RETRO, Difficulty.NORMAL, 60, GameFlowMode.QR_EXPLORATION);
        return configure(game, maker);
    }

    private EscapeGame refreshOldSeedIfNeeded(EscapeGame game, UserAccount maker) {
        if (!game.getOwner().getId().equals(maker.getId())) return game;
        List<GameStage> stages = authoring.stages(game.getId(), maker);
        boolean conditionalGuard = authoring.items(game.getId(), maker).stream()
                .anyMatch(item -> "밤톨 경비원의 체포 딱지".equals(item.getName())
                        && item.getAlternateRequiredItem() != null);
        boolean currentRevision = stages.stream().anyMatch(stage -> CURRENT_REVISION_STAGE.equals(stage.getTitle()))
                && stages.stream().anyMatch(stage -> "조합소: 두부 선물 만들기".equals(stage.getTitle()))
                && stages.stream().anyMatch(stage -> "자택경비원 등 뒤".equals(stage.getTitle()))
                && conditionalGuard;
        boolean smoothHints = game.getDifficulty() == Difficulty.NORMAL
                && game.isUnlimitedHints() && game.getHintCooldownSeconds() == 0;
        if (currentRevision && smoothHints) return game;

        for (int index = stages.size() - 1; index >= 1; index--) {
            authoring.deleteStage(game.getId(), stages.get(index).getId(), maker);
        }
        for (GameItem item : authoring.items(game.getId(), maker)) {
            authoring.deleteItem(game.getId(), item.getId(), maker);
        }
        return configure(game, maker);
    }

    private EscapeGame configure(EscapeGame game, UserAccount maker) {
        authoring.updateSettings(game.getId(), maker, game.getTitle(), game.getSlug(),
                "연두부가 집 안을 탐험해 간식 세 가지, 휴지심, 포장 끈을 모으고 조합소에서 두부의 집들이 선물을 만드는 1시간 QR 방탈출",
                "현관에서 초대장을 펼친 연두부는 먹을 것을 찾다가 큰집사에게 들켜 종이 임시 케이지에 갇힙니다. "
                        + "탈출한 뒤 인형 친구들의 부탁을 들어 간식 세 가지와 포장 재료를 모으세요. "
                        + "큰집사 앞에서 연두부가 할 수 있는 말은 오직 ‘찍’뿐입니다. "
                        + "로봇청소기와 실제 가전은 작동시키지 않고 QR만 스캔합니다.",
                "/images/dubu/dubu-bad-map.png", "#E66B55", "#55A89C", "#FFF4D6", "🐹",
                true, true, true, GameTheme.RETRO, Difficulty.NORMAL, 60, GameVisibility.LINK_ONLY);
        authoring.updateHintPolicy(game.getId(), maker, true, 3, 0);

        GameItem invitation = item(game, maker, ItemType.MAP, "두부의 집들이 초대장",
                "두부의 삐뚤빼뚤한 편지와 두부 케이지 안쪽 지도.",
                "연두부야!\n나 이사했어. 집들이 와!\n먹을 거 가져와. 아무거나!\n우리 집 지도도 그렸어!\n— 두부\n\n"
                        + "지도에는 인간 집의 방이 하나도 없다. 물병, 쳇바퀴, 밥그릇, 초록 은신처와 엉킨 화살표뿐이다.\n\n"
                        + "말 그대로 두부 케이지 안쪽 지도다. 인간 집을 돌아다니는 데는 끝까지 쓸모없다. 우선 냉장고로 가자.",
                "💌", false, true, null, "/images/dubu/dubu-bad-map.png");
        item(game, maker, ItemType.DOCUMENT, "햄스터어 회화 카드",
                "큰집사와 만났을 때 쓰는 회화 카드.",
                "하고 싶은 말이 무엇이든 입에서 나오는 말은 ‘찍’이다.\n"
                        + "큰집사에게 들키면 플레이어는 실제로도 ‘찍’으로만 대답한다.", "📣",
                false, true, null, null);

        GameItem foodMission = item(game, maker, ItemType.DOCUMENT, "집들이 간식 수색 임무",
                "두부에게 가져갈 먹을 것을 찾아야 한다.",
                "냉장고에는 사람 음식뿐인 것 같다. 그래도 안쪽의 콜라 한 병은 누군가 부탁할지도 모른다.", "👜");
        GameItem cola = item(game, maker, ItemType.FOOD, "차가운 콜라",
                "냉장고에서 발견한 작은 콜라 소품.",
                "사람 음식이라 두부 선물로 주기는 어렵다. 콜라를 좋아하는 인형이라면 반길 것이다.", "🥤");
        GameItem snackCaretaker = item(game, maker, ItemType.FOOD, "간식 ① 집사의 해바라기씨",
                "큰집사가 연두부를 유인하려고 바닥에 놓은 해바라기씨 한 봉지.",
                "임시 케이지로 가는 길에 잽싸게 챙겼다. 선물세트에 넣을 첫 번째 간식이다.", "🌻");
        GameItem escaped = item(game, maker, ItemType.KEY, "종이 케이지 탈출 성공",
                "절취선이 난 종이 벽을 뜯고 빠져나온 증표.",
                "옷방의 자택경비원에게 들키지 말고 뒤로 돌아가자.", "💥");
        GameItem arrestTicket = item(game, maker, ItemType.DOCUMENT, "밤톨 경비원의 체포 딱지",
                "자택경비원의 정면으로 갔다가 현행범이 되어 받은 쓸모없는 딱지.",
                "삐용삐용! 체포다! 임시 케이지 앞까지 연행되어 10초 동안 반성한 뒤 다시 돌아가자.\n"
                        + "아이템과 진행은 잃지 않는다. 칼은 절대 얻지 못했다.\n\n"
                        + "[경비원 앞면 QR · 메인 진행에는 영향 없음]", "🚨",
                true, false, null, null);
        GameItem toyKnife = item(game, maker, ItemType.TOOL, "밤톨 경비원의 장난감 칼",
                "경비원 뒤에서 몰래 뽑아 온 날 없는 플라스틱 칼.",
                "베놈의 검은 종이 촉수를 끊는 시늉에만 사용한다. 실제 물건은 자르지 않는다.", "🗡️");
        arrestTicket.setAlternateRequiredItem(toyKnife.getStableKey());
        arrestTicket.setAlternateScanText(
                "밤톨 경비원: ‘정면 침입! 체포… 잠깐, 내 칼이 없잖아?’\n\n"
                        + "밤톨 경비원이 빈 허리띠를 더듬다가 연두부를 슬쩍 옆으로 밀어 보낸다.\n\n"
                        + "밤톨 경비원: ‘내가 칼을 잃어버린 걸 들키면 곤란해. 너도 아무것도 못 본 거다. 쉿, 그냥 가.’\n\n"
                        + "연두부는 칼을 빼앗기지 않고 몰래 통과했다.");
        GameItem snackVenom = item(game, maker, ItemType.FOOD, "간식 ② 인형의 건조 채소",
                "베놈에게서 구해 준 인형이 건넨 건조 채소 간식.",
                "봉투 뒷면의 물병 발자국은 → ↓ ← ↑ 순서다. 화장실 2의 물병 분실함 방향 자물쇠에 쓰자.", "🥕");
        GameItem waterBottle = item(game, maker, ItemType.TOOL, "얼굴 꽃병의 잃어버린 물병",
                "방향 자물쇠가 달린 분실함에서 찾은 작은 물병 소품.",
                "거실에서 울고 있는 얼굴 꽃병에게 돌려주자.", "🍼");
        GameItem wrappingString = item(game, maker, ItemType.CUSTOM, "민트색 포장용 끈",
                "물병을 돌려받은 얼굴 꽃병이 건넨 포장 끈.",
                "집들이 선물세트의 필수 포장 재료다.", "🧵");
        GameItem snackCokeBear = item(game, maker, ItemType.FOOD, "간식 ③ 코크베어의 곡물 큐브",
                "콜라를 받은 코크베어가 답례로 준 곡물 큐브.",
                "선물세트에 넣을 세 번째 간식이다.", "🧊");
        GameItem cardboardTube = item(game, maker, ItemType.TOOL, "소파 밑 휴지심",
                "소파 밑에 굴러 들어가 있던 깨끗한 휴지심.",
                "두부가 터널로 쓸 수 있는 선물세트 재료. 소파 QR을 스캔해 직접 주웠다.", "🧻",
                true, false, null, null);
        GameItem completeGift = item(game, maker, ItemType.CUSTOM, "두부의 간식선물세트",
                "간식 세 종류와 휴지심을 넣고 민트색 끈으로 묶은 연두부표 집들이 선물.",
                "조합소가 다섯 재료를 사용 처리하고 완성품으로 돌려줬다. 두부에게 가져가자.", "🎁");
        GameItem kukiSnackTag = item(game, maker, ItemType.DOCUMENT, "쿠기의 예비 간식함 표찰",
                "두부네 문 옆 빈 간식 접시 바닥에 있던 작은 표찰.",
                "앞면: `쿠기 예비 간식함 — 큰집사 침대 아래`\n"
                        + "뒷면의 발자국 색 순서: 민트 → 노랑 → 분홍 → 파랑\n\n"
                        + "침실 보관함에 붙은 네 색 간식 카드를 이 순서대로 읽자.", "🏷️");
        GameItem flaxSeeds = item(game, maker, ItemType.FOOD, "쿠기의 아마씨 봉투",
                "큰집사가 침대 아래 예비 간식함에 보관해 둔 실제 봉투.",
                "봉투 라벨: `쿠기 / 아마씨 / 팔찌 옆 작은 간식 접시에 놓아 주세요.`\n\n"
                        + "이제야 연두부는 이 씨앗이 누구의 것이며 어디에 놓아야 하는지 알았다.", "🌾");
        GameItem spiritPassword = item(game, maker, ItemType.DOCUMENT, "옛날쿠키의 도어락 비밀번호",
                "옛날쿠키의 정령이 알려 준 아주 긴 비밀번호.",
                "외우지 말고 아래 문자열을 복사해 두부네 도어락에 그대로 붙여 넣자.", "⌨️",
                false, false, DOOR_PASSWORD, null);
        GameItem openDoor = item(game, maker, ItemType.KEY, "열린 두부네 문",
                "띠리리릭— 두부네 도어락이 열렸다.",
                "선물세트를 들고 안으로 들어가자.", "🔓");

        funQr(game, maker, "찬장의 비밀 회의", "찬장",
                "컵들이 손잡이를 맞대고 회의 중이다. 안건은 ‘햄스터에게 인간 과자를 주면 안 되는 이유’다.", "🥛");
        funQr(game, maker, "창문 끝의 바깥 우주", "창문",
                "연두부가 코를 붙였다. 오늘 목표는 우주 진출이 아니라 집들이라서 바로 돌아선다.", "🪟");
        funQr(game, maker, "정수기의 한 방울 호수", "정수기",
                "버튼은 누르지 않는다. 햄스터에게 한 방울은 이미 꽤 큰 호수다.", "💧");
        funQr(game, maker, "잠든 철갑거북", "로봇청소기",
                "납작 엎드린 철갑거북 같다. 절대 깨우거나 작동시키지 않고 QR만 보고 지나간다.", "🐢");
        funQr(game, maker, "음식물 블랙홀", "음식물처리기",
                "열거나 작동시키지 않는다. 집들이 선물까지 빨려 들어가면 곤란하다.", "🕳️");
        funQr(game, maker, "접시 온천", "식기세척기",
                "그릇들의 단체 목욕탕. 문과 버튼은 건드리지 않는다.", "♨️");
        funQr(game, maker, "전등 속 작은 태양", "전등",
                "큰집사는 방마다 작은 태양을 달아 놓았다. 잠든 큰집사가 깨지 않게 스위치는 그대로 둔다.", "☀️");
        funQr(game, maker, "거울 속 따라쟁이", "화장실 1 거울",
                "연두부가 갸웃하자 맞은편 햄스터도 갸웃한다. 수상하지만 아주 귀엽다.", "🪞");
        funQr(game, maker, "거인용 마법봉", "TV 리모컨",
                "버튼이 너무 많은 마법봉. 누르면 갑자기 인간 목소리가 날 수 있으니 관찰만 한다.", "🪄");
        funQr(game, maker, "인간용 쳇바퀴", "시계",
                "두 바늘이 하루 종일 천천히 돈다. 저 속도로는 운동이 될 리 없다.", "🕰️");

        GameStage start = authoring.stages(game.getId(), maker).get(0);
        authoring.updateStage(game.getId(), start.getId(), maker, stage(
                "현관: 두부의 엉터리 초대장",
                "두부의 초대장을 펼쳤다.\n\n"
                        + "‘연두부야! 먹을 거 가져와!’\n\n"
                        + "함께 온 지도는 물병, 쳇바퀴, 밥그릇, 은신처뿐이다. 인간 집에서는 방향 하나 알려 주지 않는다.\n\n"
                        + "연두부: ‘좋아. 먹을 거면 냉장고겠지.’",
                "가방의 초대장을 읽고 냉장고 QR을 찾으세요.",
                "현관에서 시작합니다. 편지의 ‘먹을 거’라는 말에 따라 냉장고로 가세요.",
                PuzzleType.STORY, null, 4, List.of(invitation.getStableKey()),
                foodMission.getStableKey(), false, false, StoryEffect.FADE),
                StageEntryMode.START, "");

        GameStage fridge = authoring.addStage(game.getId(), maker, stage(
                "냉장고: 사람 음식뿐",
                "치즈, 반찬, 소스, 채소… 살펴볼수록 전부 사람 음식이다.\n\n"
                        + "연두부: ‘두부한테 줄 건 하나도 없잖아.’\n\n"
                        + "그때 문 안쪽에서 작은 콜라 한 병을 발견했다. 두부 선물은 아니지만 일단 챙긴다.\n\n"
                        + "뒤에서 거대한 그림자가 길어진다.",
                "냉장고에 붙인 QR을 스캔했다면 콜라 소품을 챙기고 계속하세요.",
                "정답 문제는 없습니다. 콜라 소품만 챙기고, 냉장고 문은 닫아 주세요.",
                PuzzleType.STORY, null, 4, List.of(foodMission.getStableKey()),
                cola.getStableKey(), true, false, StoryEffect.SPOTLIGHT),
                StageEntryMode.QR, null);

        GameStage caught = authoring.addStage(game.getId(), maker, stage(
                "큰집사에게 들켰다!",
                "큰집사: ‘어? 햄스터?’\n\n"
                        + "연두부: ‘나는 두부 친구인데—’\n연두부의 입: ‘찍.’\n\n"
                        + "큰집사: ‘합사는 안 되는데. 일단 안전한 데 가자.’\n\n"
                        + "큰집사는 해바라기씨 한 봉지를 살랑살랑 흔들며 놀이방으로 걸어간다.\n\n"
                        + "연두부: ‘안 따라갈 거야.’\n연두부의 발: ‘타다다다.’",
                "배우인 큰집사는 해바라기씨 소품 하나로 플레이어를 놀이방 임시 케이지까지 유인합니다. "
                        + "플레이어는 ‘찍’으로만 대답하고, 간식 소품을 챙긴 뒤 계속하세요.",
                "큰집사가 보여 주는 간식은 한 종류뿐입니다. 실제 햄스터에게 먹이지 않습니다.",
                PuzzleType.STORY, null, 4, List.of(cola.getStableKey()),
                snackCaretaker.getStableKey(), false, false, StoryEffect.SHAKE),
                StageEntryMode.LINKED, null);
        authoring.configureStageFlow(game.getId(), fridge.getId(), maker, StageEntryMode.QR, caught.getStableKey());

        authoring.addStage(game.getId(), maker, stage(
                "놀이방: 임시 케이지를 부숴라",
                "큰집사는 연두부를 종이 임시 케이지에 넣고 침실로 갔다. 곧 코 고는 소리가 들린다.\n\n"
                        + "가까이 보니 벽은 골판지, 문은 절취선이 난 종이 봉인이다.\n\n"
                        + "연두부: ‘감옥치고는 택배 냄새가 나는데.’",
                "찢어도 되게 준비한 종이 봉인만 손으로 뜯고, 뒤에 숨긴 QR을 스캔하세요.",
                "실제 방문이나 가구를 부수지 않습니다. 절취선이 표시된 종이만 뜯으세요.",
                PuzzleType.STORY, null, 4, List.of(snackCaretaker.getStableKey()),
                escaped.getStableKey(), true, false, StoryEffect.SHAKE),
                StageEntryMode.QR, null);

        authoring.addStage(game.getId(), maker, stage(
                "자택경비원 등 뒤",
                "옷방 입구를 지키는 밤톨 경비원 인형이다. 앞가슴에는 번쩍이는 경보 QR, 등에는 아주 조용한 QR이 붙어 있다.\n\n"
                        + "연두부는 앞을 피해 인형 뒤로 빙 돌아갔다. 허리의 장난감 칼 손잡이가 바로 보인다.",
                "반드시 경비원 인형의 뒤 QR을 스캔하세요. 날 없는 장난감 칼 소품을 몰래 뽑아 챙기세요.",
                "앞면을 스캔하면 체포만 되고 칼을 얻지 못합니다. 등 뒤 QR이 정답 경로입니다.",
                PuzzleType.STORY, null, 4, List.of(escaped.getStableKey()),
                toyKnife.getStableKey(), true, false, StoryEffect.SPOTLIGHT),
                StageEntryMode.QR, null);

        authoring.addStage(game.getId(), maker, stage(
                "놀이방: 베놈에게 잡힌 인형",
                "검은 종이 촉수를 두른 베놈 인형이 작은 인형을 붙잡고 있다.\n\n"
                        + "작은 인형: ‘저 검은 띠만 끊어 주면 내 비상 간식을 줄게!’\n\n"
                        + "밤톨 경비원의 장난감 칼이라면 종이 촉수를 자르는 시늉을 할 수 있다.",
                "장난감 칼로 미리 절취된 검은 종이 띠를 자르는 시늉을 하고 손으로 떼어 내세요.",
                "실제 칼은 사용하지 않습니다. 벨크로나 절취선으로 준비한 검은 종이만 분리하세요.",
                PuzzleType.STORY, null, 4, List.of(toyKnife.getStableKey()),
                snackVenom.getStableKey(), true, false, StoryEffect.SHAKE),
                StageEntryMode.QR, null);

        authoring.addStage(game.getId(), maker, stage(
                "화장실 2: 물병 분실함",
                "세면대 옆 분실함에는 네 방향으로 움직이는 자물쇠가 달려 있다.\n\n"
                        + "구해 준 인형의 건조 채소 봉투 뒤에는 물병 발자국 네 개가 찍혀 있다.",
                "간식 ② 봉투의 발자국 순서를 방향 자물쇠에 입력하세요.",
                "봉투를 뒤집어 보세요. 순서는 → ↓ ← ↑입니다.",
                PuzzleType.DIRECTION_LOCK, "RIGHT,DOWN,LEFT,UP", 4,
                List.of(snackVenom.getStableKey()), waterBottle.getStableKey(),
                true, false, StoryEffect.NONE),
                StageEntryMode.QR, null);

        authoring.addStage(game.getId(), maker, stage(
                "거실: 울고 있는 얼굴 꽃병",
                "얼굴 꽃병이 훌쩍이고 있다.\n\n"
                        + "얼굴 꽃병: ‘내 물병을 화장실에 두고 왔어. 목말라서 웃는 얼굴이 안 나와.’\n\n"
                        + "연두부가 작은 물병을 돌려주자 꽃병이 꼴깍꼴깍 마신다.",
                "물병 소품을 꽃병 앞에 놓고, 꽃병을 천천히 돌려 웃는 얼굴이 앞으로 오게 하세요. "
                        + "웃는 얼굴 뒤에 둔 민트색 포장 끈을 챙기고 계속하세요.",
                "화장실 2의 방향 자물쇠에서 물병을 먼저 얻어야 합니다.",
                PuzzleType.STORY, null, 4, List.of(waterBottle.getStableKey()),
                wrappingString.getStableKey(), true, true, StoryEffect.FADE),
                StageEntryMode.QR, null);

        authoring.addStage(game.getId(), maker, stage(
                "화장실 1: 목마른 코크베어",
                "선반 위 코크베어 인형이 빈 컵을 들고 있다.\n\n"
                        + "코크베어: ‘코오오올라… 한 모금만….’\n\n"
                        + "냉장고에서 챙긴 콜라가 꼭 맞는 부탁이다.",
                "콜라 소품을 코크베어 앞에 놓고 계속하세요. 코크베어 옆의 곡물 큐브 간식을 챙기세요.",
                "콜라는 냉장고 QR에서 얻습니다. 두부 선물이 아니라 코크베어에게 건넬 퀘스트 아이템입니다.",
                PuzzleType.STORY, null, 4, List.of(cola.getStableKey()),
                snackCokeBear.getStableKey(), true, true, StoryEffect.FADE),
                StageEntryMode.QR, null);

        authoring.addStage(game.getId(), maker, stage(
                "조합소: 두부 선물 만들기",
                "찬장 옆 조합소에 빈 선물 봉투가 놓여 있다.\n\n"
                        + "필요한 것은 간식 세 종류, 소파 밑 휴지심, 민트색 포장 끈.\n\n"
                        + "연두부는 간식을 차곡차곡 담고 휴지심을 옆에 넣은 뒤 끈으로 야무지게 묶었다.",
                "다섯 재료가 모두 `준비됨`이면 아이템 조합하기를 누르세요. 실제 소품도 선물 봉투에 담아 묶으세요.",
                "간식 ①은 큰집사, ②는 베놈에게 잡힌 인형, ③은 코크베어에게서 얻습니다. "
                        + "휴지심은 소파 QR, 끈은 얼굴 꽃병 퀘스트 보상입니다.",
                PuzzleType.STORY, null, 4,
                List.of(snackCaretaker.getStableKey(), snackVenom.getStableKey(),
                        snackCokeBear.getStableKey(), cardboardTube.getStableKey(),
                        wrappingString.getStableKey()),
                completeGift.getStableKey(), true, true, StoryEffect.SPOTLIGHT),
                StageEntryMode.QR, null);

        authoring.addStage(game.getId(), maker, stage(
                "두부네 현관: 빈 간식 접시",
                "완성한 선물을 들고 두부네 문 앞에 왔다. 정말로 벨은 없다.\n\n"
                        + "도어락 아래에는 작은 빈 간식 접시가 하나 놓여 있다. 접시 바닥의 낡은 표찰에는 `쿠기`라는 이름과 "
                        + "`예비 간식함 — 큰집사 침대 아래`라는 글씨가 남아 있다.\n\n"
                        + "표찰을 뒤집자 민트, 노랑, 분홍, 파랑 발자국이 차례로 찍혀 있다.",
                "빈 접시에서 실제 표찰 소품을 꺼내 가방에 넣으세요. 표찰이 가리키는 침실의 예비 간식함으로 가세요.",
                "정답을 입력하는 장면이 아닙니다. 도어락 아래 빈 접시의 바닥을 확인하세요.",
                PuzzleType.STORY, null, 4, List.of(completeGift.getStableKey()),
                kukiSnackTag.getStableKey(), true, false, StoryEffect.SPOTLIGHT),
                StageEntryMode.QR, null);

        authoring.addStage(game.getId(), maker, stage(
                "침실: 쿠기의 예비 간식함",
                "큰집사 침대 아래에 `쿠기 예비 간식`이라고 적힌 작은 보관함이 있다.\n\n"
                        + "뚜껑에는 민트, 노랑, 분홍, 파랑 간식 카드 네 장이 붙어 있다. "
                        + "각 카드를 젖히면 숫자 하나가 나타난다. 어느 색부터 읽을지는 빈 접시의 표찰이 알려 준다.",
                "표찰의 발자국 색 순서대로 보관함의 네 색 카드를 젖혀 숫자를 읽고 키패드에 입력하세요. "
                        + "열리면 안에 준비한 실제 봉투를 꺼내 라벨을 읽으세요.",
                "표찰 순서는 민트 → 노랑 → 분홍 → 파랑입니다. 각 카드 안쪽 숫자는 4, 9, 0, 4입니다.",
                PuzzleType.KEYPAD, FLAX_BOX_PASSWORD, 4, List.of(kukiSnackTag.getStableKey()),
                flaxSeeds.getStableKey(), true, false, StoryEffect.NONE),
                StageEntryMode.QR, null);

        authoring.addStage(game.getId(), maker, stage(
                "두부네 앞: 옛날쿠키의 정령",
                "봉투 라벨에 적힌 대로, 두부 케이지 앞 쿠기의 털이 담긴 팔찌 곁 작은 접시에 아마씨 봉투를 놓았다.\n\n"
                        + "퐁.\n\n"
                        + "옛날쿠키의 정령: ‘뭐야. 연두부잖아. 두부네 가려고?’\n"
                        + "옛날쿠키의 정령: ‘비밀번호 알고 있지. 그런데 그거 아마씨야?’\n\n"
                        + "냠냠냠냠.\n\n"
                        + "옛날쿠키의 정령: ‘다 먹었어. 비밀번호 여기 있어. 길어서 외우면 안 돼. 그냥 복사해.’",
                "봉투를 팔찌 옆 작은 간식 접시에 놓고 아이템 건네기를 누르세요.",
                "먼저 빈 접시의 표찰을 찾고, 침실에서 쿠기의 예비 간식함을 실제로 여세요.",
                PuzzleType.STORY, null, 4, List.of(flaxSeeds.getStableKey()),
                spiritPassword.getStableKey(), true, true, StoryEffect.FADE),
                StageEntryMode.QR, null);

        GameStage door = authoring.addStage(game.getId(), maker, stage(
                "두부네 도어락",
                "두부네 문 앞이다. 역시 벨은 없다. 안에서는 바스락바스락 간식 먹는 소리만 난다.\n\n"
                        + "도어락이 아주 긴 비밀번호 입력칸을 내밀었다.",
                "가방의 옛날쿠키 비밀번호를 복사해 그대로 붙여 넣으세요.",
                "비밀번호 아이템의 복사 버튼을 사용하세요. 직접 외우거나 다시 입력할 필요가 없습니다.",
                PuzzleType.TEXT_ANSWER, DOOR_PASSWORD, 12,
                List.of(completeGift.getStableKey(), spiritPassword.getStableKey()),
                openDoor.getStableKey(), true, false, StoryEffect.SPOTLIGHT),
                StageEntryMode.QR, null);

        GameStage ending = authoring.addStage(game.getId(), maker, stage(
                "두부의 집들이",
                "띠리리릭—\n\n"
                        + "두부: ‘연두부야! 왜 이렇게 오래 걸렸어?’\n"
                        + "연두부: ‘찍찍찍찍!’\n"
                        + "두부: ‘집사한테 잡히고, 종이 감옥을 부수고, 인형들 부탁도 다 들어줬다고?’\n\n"
                        + "두부는 선물세트의 간식부터 확인했다.\n\n"
                        + "두부: ‘완벽해. 휴지심도 내 사이즈야. 들어와!’\n\n"
                        + "멀리서 큰집사의 목소리가 들린다.\n"
                        + "큰집사: ‘집들이만 하고 각자 집으로 돌아가세요. 합사는 안 됩니다!’",
                "선물세트를 두부 앞에 놓으면 집들이 성공입니다.",
                "", PuzzleType.STORY, null, 4, List.of(openDoor.getStableKey()),
                null, false, false, StoryEffect.FADE),
                StageEntryMode.LINKED, null);
        authoring.configureStageFlow(game.getId(), door.getId(), maker, StageEntryMode.QR, ending.getStableKey());

        publishing.publish(game.getId(), maker);
        return game;
    }

    private void funQr(EscapeGame game, UserAccount maker, String title, String location,
                       String clueText, String emoji) {
        item(game, maker, ItemType.DOCUMENT, title,
                location + "에서 발견한 진행과 관계없는 연두부 관찰 기록.",
                clueText + "\n\n[재미 QR · 메인 진행에는 영향 없음]", emoji,
                true, false, null, null);
    }

    private GameItem item(EscapeGame game, UserAccount maker, ItemType type, String name,
                          String description, String clueText, String emoji) {
        return item(game, maker, type, name, description, clueText, emoji,
                false, false, null, null);
    }

    private GameItem item(EscapeGame game, UserAccount maker, ItemType type, String name,
                          String description, String clueText, String emoji,
                          boolean qrEnabled, boolean initiallyOwned, String copyableText, String imageUrl) {
        return authoring.addItem(game.getId(), maker, type, name, description, clueText, emoji,
                qrEnabled, initiallyOwned, copyableText, imageUrl);
    }

    private GameAuthoringService.StageDraft stage(
            String title, String story, String instruction, String hint,
            PuzzleType puzzleType, String answer, int lockLength,
            List<String> requiredItems, String rewardItem, boolean qrEnabled,
            boolean consumeRequiredItems, StoryEffect effect) {
        return new GameAuthoringService.StageDraft(title, story, instruction, hint,
                puzzleType, answer, null, lockLength, null, rewardItem,
                qrEnabled, effect, null,
                null, null, null, null, null, null, null,
                requiredItems, consumeRequiredItems);
    }
}
