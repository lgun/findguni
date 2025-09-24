package com.findguni.controller;

import com.findguni.service.QRCodeService;
import com.google.zxing.WriterException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

@Controller
@RequestMapping("/qr")
public class QRCodeController {
    
    @Autowired
    private QRCodeService qrCodeService;
    
    @GetMapping("/generate")
    public String showQRGenerateForm() {
        return "qr-generate";
    }
    
    @PostMapping("/generate")
    public String generateQRCode(@RequestParam String itemId, 
                                @RequestParam(required = false) String customUrl,
                                HttpServletRequest request,
                                Model model) {
        try {
            String baseUrl = getBaseUrl(request);
            String qrUrl = customUrl != null && !customUrl.isEmpty() ? 
                customUrl : baseUrl + "/page/" + itemId;
            
            String qrCodeDataURL = qrCodeService.generateQRCodeDataURL(qrUrl);
            
            model.addAttribute("qrCodeImage", qrCodeDataURL);
            model.addAttribute("qrUrl", qrUrl);
            model.addAttribute("itemId", itemId);
            
        } catch (WriterException | IOException e) {
            model.addAttribute("error", "QR 코드 생성 중 오류가 발생했습니다: " + e.getMessage());
        }
        
        return "qr-generate";
    }
    
    @GetMapping("/download/{itemId}")
    public ResponseEntity<byte[]> downloadQRCode(@PathVariable String itemId,
                                               @RequestParam(required = false) String customUrl,
                                               HttpServletRequest request) {
        try {
            String baseUrl = getBaseUrl(request);
            String qrUrl = customUrl != null && !customUrl.isEmpty() ? 
                customUrl : baseUrl + "/page/" + itemId;
            
            byte[] qrCodeImage = qrCodeService.generateQRCodeImage(qrUrl);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setContentDispositionFormData("attachment", "qrcode-" + itemId + ".png");
            
            return new ResponseEntity<>(qrCodeImage, headers, HttpStatus.OK);
            
        } catch (WriterException | IOException e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        String contextPath = request.getContextPath();
        
        StringBuilder url = new StringBuilder();
        url.append(scheme).append("://").append(serverName);
        
        if ((scheme.equals("http") && serverPort != 80) || 
            (scheme.equals("https") && serverPort != 443)) {
            url.append(":").append(serverPort);
        }
        
        url.append(contextPath);
        return url.toString();
    }
}