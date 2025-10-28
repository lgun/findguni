package com.findguni.repository;

import com.findguni.model.ChoiceCondition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChoiceConditionRepository extends JpaRepository<ChoiceCondition, Long> {
    List<ChoiceCondition> findByPageId(String pageId);
    List<ChoiceCondition> findByPageIdAndChoiceText(String pageId, String choiceText);
    void deleteByPageId(String pageId);
}