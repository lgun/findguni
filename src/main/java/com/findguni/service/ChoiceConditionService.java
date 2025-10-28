package com.findguni.service;

import com.findguni.model.ChoiceCondition;
import com.findguni.repository.ChoiceConditionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChoiceConditionService {
    
    @Autowired
    private ChoiceConditionRepository choiceConditionRepository;
    
    @Autowired
    private InventoryService inventoryService;
    
    public List<ChoiceCondition> getConditionsByPageId(String pageId) {
        return choiceConditionRepository.findByPageId(pageId);
    }
    
    public List<String> getVisibleChoices(String pageId, List<String> allChoices, HttpSession session) {
        List<ChoiceCondition> conditions = getConditionsByPageId(pageId);
        
        return allChoices.stream()
                .filter(choice -> isChoiceVisible(choice, conditions, session))
                .collect(Collectors.toList());
    }
    
    private boolean isChoiceVisible(String choiceText, List<ChoiceCondition> conditions, HttpSession session) {
        List<ChoiceCondition> choiceConditions = conditions.stream()
                .filter(condition -> condition.getChoiceText().equals(choiceText))
                .collect(Collectors.toList());
        
        if (choiceConditions.isEmpty()) {
            return true; // 조건이 없으면 항상 표시
        }
        
        for (ChoiceCondition condition : choiceConditions) {
            boolean hasItem = false;
            
            if (condition.getRequiredItemId() != null) {
                hasItem = inventoryService.hasItem(session, condition.getRequiredItemId());
            }
            
            if (condition.getForbiddenItemId() != null) {
                boolean hasForbiddenItem = inventoryService.hasItem(session, condition.getForbiddenItemId());
                if (hasForbiddenItem) {
                    return false; // 금지된 아이템이 있으면 숨김
                }
            }
            
            switch (condition.getConditionType()) {
                case SHOW_IF_HAS_ITEM:
                    if (!hasItem) {
                        return false;
                    }
                    break;
                case SHOW_IF_NO_ITEM:
                    if (hasItem) {
                        return false;
                    }
                    break;
            }
        }
        
        return true;
    }
    
    @Transactional
    public void saveCondition(ChoiceCondition condition) {
        choiceConditionRepository.save(condition);
    }
    
    @Transactional
    public void deleteConditionsByPageId(String pageId) {
        choiceConditionRepository.deleteByPageId(pageId);
    }
    
    @Transactional
    public void updateConditionsForPage(String pageId, List<ChoiceCondition> newConditions) {
        // 기존 조건들 삭제
        deleteConditionsByPageId(pageId);
        
        // 새로운 조건들 저장
        for (ChoiceCondition condition : newConditions) {
            condition.setPageId(pageId);
            saveCondition(condition);
        }
    }
}