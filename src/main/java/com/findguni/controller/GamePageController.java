package com.findguni.controller;

import com.findguni.model.GamePage;
import com.findguni.service.GamePageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/page")
public class GamePageController {
    
    @Autowired
    private GamePageService gamePageService;
    
    @GetMapping("/{id}")
    public String showPage(@PathVariable String id, Model model) {
        GamePage page = gamePageService.getPage(id);
        if (page == null) {
            model.addAttribute("error", "페이지를 찾을 수 없습니다.");
            return "error";
        }
        
        model.addAttribute("page", page);
        return "game-page";
    }
    
    @PostMapping("/{id}/choice")
    public String handleChoice(@PathVariable String id, 
                              @RequestParam String choice, 
                              Model model) {
        GamePage page = gamePageService.getPage(id);
        if (page == null || page.getType() != GamePage.PageType.MULTIPLE_CHOICE) {
            return "redirect:/";
        }
        
        // 선택지에 따른 페이지 이동
        String nextPageId = page.getChoiceLinks().get(choice);
        if (nextPageId != null) {
            return "redirect:/page/" + nextPageId;
        }
        
        return "redirect:/page/" + id;
    }
    
    @PostMapping("/{id}/answer")
    public String handleAnswer(@PathVariable String id, 
                              @RequestParam String answer, 
                              Model model) {
        GamePage page = gamePageService.getPage(id);
        if (page == null || page.getType() != GamePage.PageType.TEXT_INPUT) {
            return "redirect:/";
        }
        
        // 정답 체크
        if (page.getCorrectAnswer() != null && page.getCorrectAnswer().equals(answer.trim())) {
            // 정답일 경우 성공 페이지로 이동
            if (page.getSuccessPageId() != null) {
                return "redirect:/page/" + page.getSuccessPageId();
            }
        }
        
        // 틀렸을 경우 현재 페이지로 돌아가서 오답 표시
        model.addAttribute("page", page);
        model.addAttribute("userAnswer", answer);
        model.addAttribute("isWrong", true);
        return "game-page";
    }
}