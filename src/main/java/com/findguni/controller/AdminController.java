package com.findguni.controller;

import com.findguni.model.GamePage;
import com.findguni.service.GamePageService;
import com.findguni.service.ImageService;
import com.findguni.service.ItemService;
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
    
    @Autowired
    private ItemService itemService;
    
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
        model.addAttribute("availableItems", itemService.getAllItems());
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
        System.out.println("=== 수정 폼 로딩 - 선택지 링크 디버그 ===");
        System.out.println("기존 선택지: " + page.getChoices());
        System.out.println("기존 선택지 링크 맵: " + page.getChoiceLinks());
        
        if (page.getChoices() != null && page.getChoiceLinks() != null && !page.getChoices().isEmpty() && !page.getChoiceLinks().isEmpty()) {
            StringBuilder linksBuilder = new StringBuilder();
            for (String choice : page.getChoices()) {
                String trimmedChoice = choice.trim();
                String link = page.getChoiceLinks().get(trimmedChoice);
                System.out.println("선택지 '" + trimmedChoice + "' -> 링크 '" + link + "'");
                
                if (link != null && !link.trim().isEmpty()) {
                    linksBuilder.append(link.trim());
                }
                linksBuilder.append("\n");
            }
            if (linksBuilder.length() > 0) {
                choiceLinksText = linksBuilder.toString().trim();
            }
            System.out.println("생성된 choiceLinksText: '" + choiceLinksText + "'");
        } else {
            System.out.println("선택지 또는 링크 맵이 null이거나 비어있음");
            System.out.println("선택지 개수: " + (page.getChoices() != null ? page.getChoices().size() : "null"));
            System.out.println("링크 맵 크기: " + (page.getChoiceLinks() != null ? page.getChoiceLinks().size() : "null"));
        }
        
        // 디버그: 실제 아이템 데이터 확인
        System.out.println("=== 페이지 수정 - 아이템 데이터 디버그 ===");
        System.out.println("Page ID: " + page.getId());
        System.out.println("Required Items: " + page.getRequiredItems());
        System.out.println("Reward Items: " + page.getRewardItems()); 
        System.out.println("Remove Items: " + page.getRemoveItems());
        
        if (page.getRewardItems() != null) {
            System.out.println("Reward Items 상세:");
            for (int i = 0; i < page.getRewardItems().size(); i++) {
                String item = page.getRewardItems().get(i);
                System.out.println("  [" + i + "]: '" + item + "' (length: " + (item != null ? item.length() : "null") + ")");
            }
        }
        
        model.addAttribute("page", page);
        model.addAttribute("pageTypes", GamePage.PageType.values());
        model.addAttribute("availableImages", imageService.getAvailableImages());
        model.addAttribute("availableItems", itemService.getAllItems());
        model.addAttribute("choiceLinksText", choiceLinksText);
        
        System.out.println("=== Model에 전달되는 최종 choiceLinksText ===");
        System.out.println("'" + choiceLinksText + "'");
        System.out.println("길이: " + choiceLinksText.length());
        
        return "admin/page-form";
    }
    
    @PostMapping("/pages")
    public String savePage(@ModelAttribute GamePage page, 
                          @RequestParam(required = false) String choicesText,
                          @RequestParam(required = false) String choiceLinksText,
                          @RequestParam(required = false) String selectedImage,
                          @RequestParam(required = false) String question,
                          @RequestParam(required = false) String requiredItems,
                          @RequestParam(required = false) String rewardItems,
                          @RequestParam(required = false) String removeItems) {
        
        // 선택지 처리
        if (choicesText != null && !choicesText.trim().isEmpty()) {
            String[] choiceArray = choicesText.split("\n");
            page.setChoices(Arrays.asList(choiceArray));
        }
        
        // 선택지 링크 처리 (선택지와 링크를 순서대로 매칭)
        System.out.println("=== 선택지 링크 처리 디버그 ===");
        System.out.println("choicesText: '" + choicesText + "'");
        System.out.println("choiceLinksText: '" + choiceLinksText + "'");
        
        if (choicesText != null && choiceLinksText != null && 
            !choicesText.trim().isEmpty() && !choiceLinksText.trim().isEmpty()) {
            
            String[] choices = choicesText.split("\n");
            String[] links = choiceLinksText.split("\n");
            
            System.out.println("choices 배열: " + Arrays.toString(choices));
            System.out.println("links 배열: " + Arrays.toString(links));
            
            Map<String, String> choiceLinksMap = new HashMap<>();
            int minLength = Math.min(choices.length, links.length);
            System.out.println("처리할 최소 길이: " + minLength);
            
            for (int i = 0; i < minLength; i++) {
                String choice = choices[i].trim();
                String link = links[i].trim();
                System.out.println("처리 중 [" + i + "]: choice='" + choice + "', link='" + link + "'");
                if (!choice.isEmpty() && !link.isEmpty()) {
                    choiceLinksMap.put(choice, link);
                    System.out.println("맵에 추가됨: " + choice + " -> " + link);
                }
            }
            System.out.println("최종 choiceLinksMap: " + choiceLinksMap);
            page.setChoiceLinks(choiceLinksMap);
        } else {
            System.out.println("선택지 링크 처리 조건에 맞지 않음");
        }
        
        // 선택된 이미지가 있으면 경로 설정
        if (selectedImage != null && !selectedImage.isEmpty()) {
            page.setImageUrl(imageService.getImagePath(selectedImage));
        }
        
        // question 설정 (파라미터로 받은 값만 사용)
        page.setQuestion(question != null && !question.trim().isEmpty() ? question.trim() : null);
        
        // 필요한 아이템 처리
        if (requiredItems != null && !requiredItems.trim().isEmpty()) {
            String[] itemsArray = requiredItems.split(",");
            List<String> itemsList = Arrays.stream(itemsArray)
                    .map(String::trim)
                    .filter(item -> !item.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
            page.setRequiredItems(itemsList.isEmpty() ? null : itemsList);
        } else {
            page.setRequiredItems(null);
        }
        
        // 지급 아이템 처리
        System.out.println("=== 저장 시 지급 아이템 처리 ===");
        System.out.println("받은 rewardItems 파라미터: '" + rewardItems + "'");
        
        if (rewardItems != null && !rewardItems.trim().isEmpty()) {
            String[] itemsArray = rewardItems.split(",");
            System.out.println("split 결과: " + Arrays.toString(itemsArray));
            
            List<String> itemsList = Arrays.stream(itemsArray)
                    .map(String::trim)
                    .filter(item -> !item.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
            
            System.out.println("필터링 후 리스트: " + itemsList);
            page.setRewardItems(itemsList.isEmpty() ? null : itemsList);
            System.out.println("최종 설정된 값: " + page.getRewardItems());
        } else {
            page.setRewardItems(null);
            System.out.println("null로 설정됨");
        }
        
        // 제거 아이템 처리
        if (removeItems != null && !removeItems.trim().isEmpty()) {
            String[] itemsArray = removeItems.split(",");
            List<String> itemsList = Arrays.stream(itemsArray)
                    .map(String::trim)
                    .filter(item -> !item.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
            page.setRemoveItems(itemsList.isEmpty() ? null : itemsList);
        } else {
            page.setRemoveItems(null);
        }
        
        if (page.getId() == null || page.getId().isEmpty()) {
            return "redirect:/admin/pages";
        }
        
        System.out.println("=== 저장 전 페이지 데이터 ===");
        System.out.println("선택지: " + page.getChoices());
        System.out.println("선택지 링크 맵: " + page.getChoiceLinks());
        
        gamePageService.addPage(page);
        
        // 저장 후 다시 불러와서 확인
        GamePage savedPage = gamePageService.getPage(page.getId());
        System.out.println("=== 저장 후 DB에서 불러온 데이터 ===");
        System.out.println("선택지: " + savedPage.getChoices());
        System.out.println("선택지 링크 맵: " + savedPage.getChoiceLinks());
        
        return "redirect:/admin/pages";
    }
    
    @PostMapping("/pages/{id}/delete")
    public String deletePage(@PathVariable String id) {
        gamePageService.deletePage(id);
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