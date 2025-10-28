package com.findguni.controller;

import com.findguni.model.GamePage;
import com.findguni.model.Item;
import com.findguni.service.GamePageService;
import com.findguni.service.ItemService;
import com.findguni.service.InventoryService;
import com.findguni.service.ChoiceConditionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/page")
public class GamePageController {
    
    @Autowired
    private GamePageService gamePageService;
    
    @Autowired
    private ItemService itemService;
    
    @Autowired
    private InventoryService inventoryService;
    
    @Autowired
    private ChoiceConditionService choiceConditionService;
    
    @GetMapping("/{id}")
    public String showPage(@PathVariable String id, Model model, HttpSession session) {
        GamePage page = gamePageService.getPage(id);
        if (page == null) {
            model.addAttribute("error", "페이지를 찾을 수 없습니다.");
            return "error";
        }
        
        // 아이템 조건 체크
        if (page.getRequiredItems() != null && !page.getRequiredItems().isEmpty()) {
            boolean hasAllItems = true;
            List<String> missingItems = new ArrayList<>();
            
            for (String requiredItemId : page.getRequiredItems()) {
                if (!inventoryService.hasItem(session, requiredItemId)) {
                    hasAllItems = false;
                    itemService.getItemById(requiredItemId).ifPresent(item -> 
                        missingItems.add(item.getName())
                    );
                }
            }
            
            if (!hasAllItems) {
                // 필요한 아이템이 없는 경우
                String message = page.getNoItemMessage();
                if (message == null || message.trim().isEmpty()) {
                    message = "이 페이지에 접근하려면 다음 아이템이 필요합니다: " + String.join(", ", missingItems);
                }
                
                model.addAttribute("error", message);
                model.addAttribute("missingItems", missingItems);
                
                // 대체 페이지가 있으면 리다이렉트
                if (page.getAlternativePageId() != null && !page.getAlternativePageId().trim().isEmpty()) {
                    return "redirect:/page/" + page.getAlternativePageId();
                }
                
                return "error";
            }
        }
        
        // 페이지 진입시 아이템 제거 처리
        if (page.getRemoveItems() != null && !page.getRemoveItems().isEmpty()) {
            inventoryService.removeItems(session, page.getRemoveItems());
        }
        
        // 페이지 진입시 아이템 지급 처리
        if (page.getRewardItems() != null && !page.getRewardItems().isEmpty()) {
            List<String> newlyObtainedItems = new ArrayList<>();
            for (String itemId : page.getRewardItems()) {
                boolean isNewItem = inventoryService.addItem(session, itemId);
                if (isNewItem) {
                    newlyObtainedItems.add(itemId);
                }
            }
            if (!newlyObtainedItems.isEmpty()) {
                List<Item> newItems = new ArrayList<>();
                for (String itemId : newlyObtainedItems) {
                    itemService.getItemById(itemId).ifPresent(newItems::add);
                }
                model.addAttribute("newItems", newItems);
            }
        }
        
        // 기존 아이템 시스템 (페이지별 아이템) - 하위 호환성을 위해 유지
        List<Item> pageItems = itemService.getItemsByPageId(id);
        if (!pageItems.isEmpty()) {
            List<Item> legacyNewItems = new ArrayList<>();
            for (Item item : pageItems) {
                boolean isNewItem = inventoryService.addItem(session, item.getId());
                if (isNewItem) {
                    legacyNewItems.add(item);
                }
            }
            if (!legacyNewItems.isEmpty()) {
                model.addAttribute("legacyNewItems", legacyNewItems);
            }
        }
        
        // 선택지 필터링 (아이템 조건 기반)
        if (page.getType() == GamePage.PageType.MULTIPLE_CHOICE && page.getChoices() != null) {
            List<String> visibleChoices = choiceConditionService.getVisibleChoices(
                page.getId(), page.getChoices(), session);
            model.addAttribute("visibleChoices", visibleChoices);
        } else {
            model.addAttribute("visibleChoices", page.getChoices());
        }
        
        // 사용자 인벤토리 정보도 전달 (디버깅 및 템플릿에서 활용)
        model.addAttribute("userInventory", inventoryService.getInventoryItems(session));
        
        model.addAttribute("page", page);
        return "game-page";
    }
    
    @GetMapping("/{id}/choice")
    public String handleChoice(@PathVariable String id, 
                              @RequestParam String choice, 
                              Model model) {
        GamePage page = gamePageService.getPage(id);
        if (page == null || page.getType() != GamePage.PageType.MULTIPLE_CHOICE) {
            return "redirect:/qr-scan";
        }
        
        // 디버그 로그
        System.out.println("선택형 문제 - 페이지 ID: " + id);
        System.out.println("사용자 선택: '" + choice + "'");
        System.out.println("선택지 링크 맵: " + page.getChoiceLinks());
        
        // 선택지에 따른 페이지 이동
        String nextPageId = null;
        if (page.getChoiceLinks() != null) {
            nextPageId = page.getChoiceLinks().get(choice);
        }
        
        System.out.println("찾은 다음 페이지 ID: " + nextPageId);
        
        if (nextPageId != null && !nextPageId.trim().isEmpty()) {
            System.out.println(nextPageId + "로 이동");
            try {
                String encodedId = URLEncoder.encode(nextPageId, StandardCharsets.UTF_8);
                System.out.println("인코딩된 ID: " + encodedId);
                return "redirect:/page/" + encodedId;
            } catch (Exception e) {
                System.out.println("인코딩 실패: " + e.getMessage());
                return "redirect:/qr-scan";
            }
        }
        
        System.out.println("링크를 찾지 못함. 현재 페이지로 돌아감");
        try {
            String encodedId = URLEncoder.encode(id, StandardCharsets.UTF_8);
            return "redirect:/page/" + encodedId;
        } catch (Exception e) {
            return "redirect:/qr-scan";
        }
    }
    
    @PostMapping("/{id}/answer")
    public String handleAnswer(@PathVariable String id, 
                              @RequestParam String answer, 
                              Model model) {
        GamePage page = gamePageService.getPage(id);
        if (page == null || page.getType() != GamePage.PageType.TEXT_INPUT) {
            return "redirect:/qr-scan";
        }
        
        // 정답 체크
        System.out.println("=== 주관식 정답 체크 ===");
        System.out.println("페이지 ID: " + id);
        System.out.println("설정된 정답: '" + page.getCorrectAnswer() + "'");
        System.out.println("사용자 입력: '" + answer + "'");
        System.out.println("successPageId: '" + page.getSuccessPageId() + "'");
        
        if (page.getCorrectAnswer() != null && answer != null) {
            String correctAnswer = page.getCorrectAnswer().trim();
            String userAnswer = answer.trim();
            
            System.out.println("정리된 정답: '" + correctAnswer + "'");
            System.out.println("정리된 사용자 답: '" + userAnswer + "'");
            
            if (correctAnswer.equals(userAnswer)) {
                System.out.println("정답 일치!");
                if (page.getSuccessPageId() != null && !page.getSuccessPageId().trim().isEmpty()) {
                    String targetPage = page.getSuccessPageId().trim();
                    System.out.println("리디렉션: /page/" + targetPage);
                    try {
                        String encodedId = URLEncoder.encode(targetPage, StandardCharsets.UTF_8);
                        System.out.println("인코딩된 ID: " + encodedId);
                        return "redirect:/page/" + encodedId;
                    } catch (Exception e) {
                        System.out.println("인코딩 실패: " + e.getMessage());
                        return "redirect:/qr-scan";
                    }
                } else {
                    System.out.println("successPageId가 없어서 QR 스캔으로 이동");
                    return "redirect:/qr-scan";
                }
            } else {
                System.out.println("정답 불일치");
            }
        }
        
        // 틀렸을 경우 현재 페이지로 돌아가서 오답 표시
        model.addAttribute("page", page);
        model.addAttribute("userAnswer", answer);
        model.addAttribute("isWrong", true);
        return "game-page";
    }
}