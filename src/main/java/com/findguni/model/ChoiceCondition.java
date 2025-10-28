package com.findguni.model;

import jakarta.persistence.*;

@Entity
@Table(name = "choice_conditions")
public class ChoiceCondition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "page_id", nullable = false)
    private String pageId;
    
    @Column(name = "choice_text", nullable = false)
    private String choiceText;
    
    @Column(name = "required_item_id")
    private String requiredItemId; // 이 선택지를 보기 위해 필요한 아이템
    
    @Column(name = "forbidden_item_id")
    private String forbiddenItemId; // 이 선택지를 숨기는 아이템 (이 아이템이 있으면 선택지 숨김)
    
    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", nullable = false)
    private ConditionType conditionType;
    
    public enum ConditionType {
        SHOW_IF_HAS_ITEM,    // 아이템이 있을 때만 표시
        SHOW_IF_NO_ITEM      // 아이템이 없을 때만 표시
    }
    
    public ChoiceCondition() {}
    
    public ChoiceCondition(String pageId, String choiceText, String requiredItemId, ConditionType conditionType) {
        this.pageId = pageId;
        this.choiceText = choiceText;
        this.requiredItemId = requiredItemId;
        this.conditionType = conditionType;
    }
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getPageId() {
        return pageId;
    }
    
    public void setPageId(String pageId) {
        this.pageId = pageId;
    }
    
    public String getChoiceText() {
        return choiceText;
    }
    
    public void setChoiceText(String choiceText) {
        this.choiceText = choiceText;
    }
    
    public String getRequiredItemId() {
        return requiredItemId;
    }
    
    public void setRequiredItemId(String requiredItemId) {
        this.requiredItemId = requiredItemId;
    }
    
    public String getForbiddenItemId() {
        return forbiddenItemId;
    }
    
    public void setForbiddenItemId(String forbiddenItemId) {
        this.forbiddenItemId = forbiddenItemId;
    }
    
    public ConditionType getConditionType() {
        return conditionType;
    }
    
    public void setConditionType(ConditionType conditionType) {
        this.conditionType = conditionType;
    }
}