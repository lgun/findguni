package com.findguni.service;

import com.findguni.model.GamePage;
import com.findguni.repository.GamePageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.util.*;

@Service
public class GamePageService {
    
    @Autowired
    private GamePageRepository gamePageRepository;
    
    // 자동으로 기본 페이지를 생성하지 않음 - 관리자가 필요시 수동으로 생성
    // @PostConstruct 제거하여 서버 시작 시 자동 실행 방지
    
    @Transactional
    public void createDefaultPages() {
        // 단서 1 - 집안 수색 시작
        GamePage clue1 = new GamePage("clue1", "🏠 집안 수색", "/images/house_search.jpg", 
            "집안을 샅샅이 뒤져보기 시작했습니다.\n\n" +
            "구니의 책상 서랍에서 이상한 메모를 발견했습니다!\n\n" +
            "메모 내용: \"구니가 가장 좋아하는 숫자... 하트 모양처럼 생겼다고 했는데...\"\n\n" +
            "이 메모가 다음 단서로 가는 열쇠일까요?", 
            GamePage.PageType.TEXT_INPUT);
        clue1.setQuestion("구니가 가장 좋아하는 숫자는?");
        clue1.setCorrectAnswer("3");
        clue1.setSuccessPageId("clue2");
        
        // 단서 2 - 꽃밭에서 발견한 쪽지
        GamePage clue2 = new GamePage("clue2", "🌺 꽃밭의 비밀 쪽지", "/images/garden.jpg", 
            "훌륭해요! 다음 단서를 찾았네요.\n\n" +
            "꽃밭에서 구니의 쪽지를 발견했습니다:\n" +
            "\"납치범이 내가 좋아하는 색깔의 꽃 근처에 다음 단서를 숨겨뒀대...\n" +
            "무지개의 첫 번째 색이야!\"\n\n" +
            "구니를 구하기 위해 올바른 꽃을 선택하세요!", 
            GamePage.PageType.MULTIPLE_CHOICE);
        clue2.setQuestion("구니가 좋아하는 색깔의 꽃은?");
        clue2.setChoices(Arrays.asList("빨간 장미", "노란 해바라기", "파란 수국", "보라 라벤더"));
        clue2.setChoiceLinks(Map.of(
            "빨간 장미", "clue3",
            "노란 해바라기", "wrong1", 
            "파란 수국", "wrong2",
            "보라 라벤더", "wrong3"
        ));
        
        // 단서 3 - 구니의 일기장
        GamePage clue3 = new GamePage("clue3", "📖 구니의 비밀 일기", "/images/diary.jpg", 
            "빨간 장미 근처에서 구니의 일기장을 찾았습니다!\n\n" +
            "일기 내용:\n" +
            "\"오늘 밤하늘을 보면서 생각했어. 내가 가장 좋아하는 시간은 언제일까?\n" +
            "별들이 반짝이고 달이 떠 있는, 하루 중 가장 고요한 그 시간...\"\n\n" +
            "일기의 힌트로 다음 단서를 찾아보세요.", 
            GamePage.PageType.TEXT_INPUT);
        clue3.setQuestion("구니가 가장 좋아하는 시간은?");
        clue3.setCorrectAnswer("밤");
        clue3.setSuccessPageId("final");
        
        // 최종 발견 - 구니와의 만남
        GamePage finalPage = new GamePage("final", "🎉 구니를 찾았다!", "/images/guni_found.jpg", 
            "드디어 구니를 찾았습니다!\n\n" +
            "하지만 구니가 웃고 있네요...\n\n" +
            "구니: \"사실은... 내가 다 계획한 거였어! 뽀뽀 1억번 받고 싶어서 말이야~ 💋\"\n\n" +
            "구니의 자작극이었던 것입니다!\n" +
            "그래도 구니를 무사히 '구출'해서 다행이네요! 😊", 
            GamePage.PageType.STORY_ONLY);
        
        // 오답 페이지들
        GamePage wrong1 = new GamePage("wrong1", "❌ 틀렸어요", "/images/wrong.jpg", 
            "앗, 틀렸네요!\n\n노란 해바라기 근처를 찾아봤지만 아무것도 없었어요.\n\n다시 생각해보세요!", 
            GamePage.PageType.STORY_ONLY);
        
        GamePage wrong2 = new GamePage("wrong2", "❌ 틀렸어요", "/images/wrong.jpg", 
            "아쉽게도 틀렸어요!\n\n파란 수국 주변에는 단서가 없었습니다.\n\n다른 선택을 해보세요!", 
            GamePage.PageType.STORY_ONLY);
        
        GamePage wrong3 = new GamePage("wrong3", "❌ 틀렸어요", "/images/wrong.jpg", 
            "틀렸네요!\n\n보라 라벤더 근처에는 아무것도 없었어요.\n\n다시 시도해보세요!", 
            GamePage.PageType.STORY_ONLY);
        
        gamePageRepository.saveAll(Arrays.asList(
            clue1, clue2, clue3, finalPage, wrong1, wrong2, wrong3
        ));
    }
    
    public GamePage getPage(String id) {
        return gamePageRepository.findById(id).orElse(null);
    }
    
    public List<GamePage> getAllPages() {
        return gamePageRepository.findAll();
    }
    
    @Transactional
    public GamePage addPage(GamePage page) {
        return gamePageRepository.save(page);
    }
    
    @Transactional
    public GamePage updatePage(GamePage page) {
        return gamePageRepository.save(page);
    }
    
    @Transactional
    public void deletePage(String id) {
        gamePageRepository.deleteById(id);
    }
    
    public boolean pageExists(String id) {
        return gamePageRepository.existsById(id);
    }
}