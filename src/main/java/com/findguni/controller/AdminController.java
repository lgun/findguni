package com.findguni.controller;

import com.findguni.model.GamePage;
import com.findguni.service.GamePageService;
import com.findguni.service.ImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/admin")
public class AdminController {
    
    @Autowired
    private GamePageService gamePageService;
    
    @Autowired
    private ImageService imageService;
    
    @GetMapping("")
    public String adminHome() {
        return "admin";
    }
    
    @GetMapping("/pages")
    public String listPages(Model model) {
        model.addAttribute("pages", gamePageService.getAllPages());
        return "admin/page-list";
    }
    
    @GetMapping("/pages/new")
    public String showCreateForm(Model model) {
        model.addAttribute("page", new GamePage());
        model.addAttribute("pageTypes", GamePage.PageType.values());
        model.addAttribute("availableImages", imageService.getAvailableImages());
        return "admin/page-form";
    }
    
    @GetMapping("/pages/{id}/edit")
    public String showEditForm(@PathVariable String id, Model model) {
        GamePage page = gamePageService.getPage(id);
        if (page == null) {
            return "redirect:/admin/pages";
        }
        model.addAttribute("page", page);
        model.addAttribute("pageTypes", GamePage.PageType.values());
        model.addAttribute("availableImages", imageService.getAvailableImages());
        return "admin/page-form";
    }
    
    @PostMapping("/pages")
    public String savePage(@ModelAttribute GamePage page, 
                          @RequestParam(required = false) String choicesText,
                          @RequestParam(required = false) String choiceLinksText,
                          @RequestParam(required = false) String selectedImage) {
        
        // 선택지 처리
        if (choicesText != null && !choicesText.trim().isEmpty()) {
            String[] choiceArray = choicesText.split("\n");
            page.setChoices(Arrays.asList(choiceArray));
        }
        
        // 선택지 링크 처리 (형식: "선택지1:페이지1\n선택지2:페이지2")
        if (choiceLinksText != null && !choiceLinksText.trim().isEmpty()) {
            Map<String, String> choiceLinksMap = new HashMap<>();
            String[] linkLines = choiceLinksText.split("\n");
            for (String line : linkLines) {
                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        choiceLinksMap.put(parts[0].trim(), parts[1].trim());
                    }
                }
            }
            page.setChoiceLinks(choiceLinksMap);
        }
        
        // 선택된 이미지가 있으면 경로 설정
        if (selectedImage != null && !selectedImage.isEmpty()) {
            page.setImageUrl(imageService.getImagePath(selectedImage));
        }
        
        if (page.getId() == null || page.getId().isEmpty()) {
            return "redirect:/admin/pages";
        }
        
        gamePageService.addPage(page);
        return "redirect:/admin/pages";
    }
    
    @PostMapping("/pages/{id}/delete")
    public String deletePage(@PathVariable String id) {
        gamePageService.deletePage(id);
        return "redirect:/admin/pages";
    }
    
    @PostMapping("/pages/reset-sample-data")
    public String resetSampleData() {
        // 모든 기존 페이지 삭제
        gamePageService.getAllPages().forEach(page -> gamePageService.deletePage(page.getId()));
        
        // 샘플 페이지들 생성
        gamePageService.createDefaultPages();
        
        return "redirect:/admin/pages";
    }
}