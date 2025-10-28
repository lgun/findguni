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
    
    @ElementCollection
    @CollectionTable(name = "game_page_required_items", joinColumns = @JoinColumn(name = "page_id"))
    @Column(name = "item_id")
    private List<String> requiredItems; // 이 페이지에 접근하기 위해 필요한 아이템들
    
    @Column(name = "no_item_message")
    private String noItemMessage; // 필요한 아이템이 없을 때 표시할 메시지
    
    @Column(name = "alternative_page_id")
    private String alternativePageId; // 아이템이 없을 때 이동할 대체 페이지 ID
    
    @ElementCollection
    @CollectionTable(name = "game_page_reward_items", joinColumns = @JoinColumn(name = "page_id"))
    @Column(name = "item_id")
    private List<String> rewardItems; // 이 페이지에 진입할 때 지급할 아이템들
    
    @ElementCollection
    @CollectionTable(name = "game_page_remove_items", joinColumns = @JoinColumn(name = "page_id"))
    @Column(name = "item_id")
    private List<String> removeItems; // 이 페이지에 진입할 때 제거할 아이템들
    
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
    
    public List<String> getRequiredItems() {
        return requiredItems;
    }
    
    public void setRequiredItems(List<String> requiredItems) {
        this.requiredItems = requiredItems;
    }
    
    public String getNoItemMessage() {
        return noItemMessage;
    }
    
    public void setNoItemMessage(String noItemMessage) {
        this.noItemMessage = noItemMessage;
    }
    
    public String getAlternativePageId() {
        return alternativePageId;
    }
    
    public void setAlternativePageId(String alternativePageId) {
        this.alternativePageId = alternativePageId;
    }
    
    public List<String> getRewardItems() {
        return rewardItems;
    }
    
    public void setRewardItems(List<String> rewardItems) {
        this.rewardItems = rewardItems;
    }
    
    public List<String> getRemoveItems() {
        return removeItems;
    }
    
    public void setRemoveItems(List<String> removeItems) {
        this.removeItems = removeItems;
    }
}