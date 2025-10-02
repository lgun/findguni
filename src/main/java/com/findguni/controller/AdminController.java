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
import java.util.List;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

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
        
        // 기존 choiceLinks를 선택지 순서대로 분리
        String choiceLinksText = "";
        if (page.getChoices() != null && page.getChoiceLinks() != null) {
            StringBuilder linksBuilder = new StringBuilder();
            for (String choice : page.getChoices()) {
                String link = page.getChoiceLinks().get(choice);
                linksBuilder.append(link != null ? link : "").append("\n");
            }
            choiceLinksText = linksBuilder.toString().trim();
        }
        
        model.addAttribute("page", page);
        model.addAttribute("pageTypes", GamePage.PageType.values());
        model.addAttribute("availableImages", imageService.getAvailableImages());
        model.addAttribute("choiceLinksText", choiceLinksText);
        return "admin/page-form";
    }
    
    @PostMapping("/pages")
    public String savePage(@ModelAttribute GamePage page, 
                          @RequestParam(required = false) String choicesText,
                          @RequestParam(required = false) String choiceLinksText,
                          @RequestParam(required = false) String selectedImage,
                          @RequestParam(required = false) String question) {
        
        // 선택지 처리
        if (choicesText != null && !choicesText.trim().isEmpty()) {
            String[] choiceArray = choicesText.split("\n");
            page.setChoices(Arrays.asList(choiceArray));
        }
        
        // 선택지 링크 처리 (선택지와 링크를 순서대로 매칭)
        if (choicesText != null && choiceLinksText != null && 
            !choicesText.trim().isEmpty() && !choiceLinksText.trim().isEmpty()) {
            
            String[] choices = choicesText.split("\n");
            String[] links = choiceLinksText.split("\n");
            
            Map<String, String> choiceLinksMap = new HashMap<>();
            int minLength = Math.min(choices.length, links.length);
            
            for (int i = 0; i < minLength; i++) {
                String choice = choices[i].trim();
                String link = links[i].trim();
                if (!choice.isEmpty() && !link.isEmpty()) {
                    choiceLinksMap.put(choice, link);
                }
            }
            page.setChoiceLinks(choiceLinksMap);
        }
        
        // 선택된 이미지가 있으면 경로 설정
        if (selectedImage != null && !selectedImage.isEmpty()) {
            page.setImageUrl(imageService.getImagePath(selectedImage));
        }
        
        // question 설정 (파라미터로 받은 값만 사용)
        page.setQuestion(question != null && !question.trim().isEmpty() ? question.trim() : null);
        
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
    
    @PostMapping("/pages/qr-codes/download")
    public ResponseEntity<byte[]> downloadQRCodes(@RequestParam String baseUrl) {
        try {
            List<GamePage> pages = gamePageService.getAllPages();
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ZipOutputStream zipOut = new ZipOutputStream(baos);
            
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            
            for (GamePage page : pages) {
                // 한글 페이지 ID를 URL 인코딩
                String encodedPageId = URLEncoder.encode(page.getId(), StandardCharsets.UTF_8);
                String pageUrl = baseUrl + "/page/" + encodedPageId;
                
                // QR 코드 생성
                BitMatrix bitMatrix = qrCodeWriter.encode(pageUrl, BarcodeFormat.QR_CODE, 300, 300);
                
                // PNG 이미지로 변환
                ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
                MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
                
                // ZIP에 파일 추가
                ZipEntry zipEntry = new ZipEntry(page.getId() + "_" + page.getTitle().replaceAll("[^a-zA-Z0-9가-힣]", "_") + ".png");
                zipOut.putNextEntry(zipEntry);
                zipOut.write(pngOutputStream.toByteArray());
                zipOut.closeEntry();
                
                pngOutputStream.close();
            }
            
            zipOut.close();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "qr_codes.zip");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(baos.toByteArray());
                    
        } catch (WriterException | IOException e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}