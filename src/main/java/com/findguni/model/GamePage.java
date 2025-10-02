package com.findguni.model;

import jakarta.persistence.*;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "game_pages")
public class GamePage {
    @Id
    private String id;
    
    @Column(nullable = false)
    private String title;
    
    @Column(name = "image_url")
    private String imageUrl;
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PageType type;
    
    private String question;
    
    @ElementCollection
    @CollectionTable(name = "game_page_choices", joinColumns = @JoinColumn(name = "page_id"))
    @Column(name = "choice_text")
    private List<String> choices;
    
    @ElementCollection
    @CollectionTable(name = "game_page_choice_links", joinColumns = @JoinColumn(name = "page_id"))
    @MapKeyColumn(name = "choice_text")
    @Column(name = "target_page_id")
    private Map<String, String> choiceLinks; // 선택지별 링크 매핑
    
    @Column(name = "correct_answer")
    private String correctAnswer;
    
    @Column(name = "success_page_id")
    private String successPageId; // 정답 시 이동할 페이지 ID
    
    @Column(name = "next_page_id")
    private String nextPageId; // 다음 페이지 ID
    
    @Column(name = "next_button_text")
    private String nextButtonText; // 다음 페이지 버튼 텍스트
    
    public enum PageType {
        STORY_ONLY,      // 스토리만 있는 페이지
        MULTIPLE_CHOICE, // 선택형 문제
        TEXT_INPUT       // 주관식 문제
    }
    
    public GamePage() {}
    
    public GamePage(String id, String title, String imageUrl, String content, PageType type) {
        this.id = id;
        this.title = title;
        this.imageUrl = imageUrl;
        this.content = content;
        this.type = type;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getImageUrl() {
        return imageUrl;
    }
    
    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public PageType getType() {
        return type;
    }
    
    public void setType(PageType type) {
        this.type = type;
    }
    
    public String getQuestion() {
        return question;
    }
    
    public void setQuestion(String question) {
        this.question = question;
    }
    
    public List<String> getChoices() {
        return choices;
    }
    
    public void setChoices(List<String> choices) {
        this.choices = choices;
    }
    
    public Map<String, String> getChoiceLinks() {
        return choiceLinks;
    }
    
    public void setChoiceLinks(Map<String, String> choiceLinks) {
        this.choiceLinks = choiceLinks;
    }
    
    public String getCorrectAnswer() {
        return correctAnswer;
    }
    
    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }
    
    public String getSuccessPageId() {
        return successPageId;
    }
    
    public void setSuccessPageId(String successPageId) {
        this.successPageId = successPageId;
    }
    
    public String getNextPageId() {
        return nextPageId;
    }
    
    public void setNextPageId(String nextPageId) {
        this.nextPageId = nextPageId;
    }
    
    public String getNextButtonText() {
        return nextButtonText;
    }
    
    public void setNextButtonText(String nextButtonText) {
        this.nextButtonText = nextButtonText;
    }
}