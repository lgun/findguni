package com.findguni.service;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class ImageService {
    
    private final ResourceLoader resourceLoader;
    private static final String IMAGES_PATH = "classpath:static/images/";
    private static final String ITEM_IMAGES_PATH = "classpath:static/images/items/";
    private static final List<String> SUPPORTED_EXTENSIONS = Arrays.asList(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp");
    
    public ImageService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
    
    public List<String> getAvailableImages() {
        List<String> imageFiles = new ArrayList<>();
        
        try {
            Resource resource = resourceLoader.getResource(IMAGES_PATH);
            if (resource.exists() && resource.getFile().isDirectory()) {
                File imageDir = resource.getFile();
                File[] files = imageDir.listFiles();
                
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile() && isImageFile(file.getName())) {
                            imageFiles.add(file.getName());
                        }
                    }
                }
            }
        } catch (IOException e) {
            // 폴더가 없거나 접근할 수 없는 경우 빈 리스트 반환
        }
        
        // 기본 이미지들 추가 (실제 파일이 없어도 옵션으로 제공)
        if (imageFiles.isEmpty()) {
            imageFiles.addAll(Arrays.asList(
                "treasure1.jpg", "treasure2.jpg", "treasure3.jpg", 
                "treasure4.jpg", "treasure5.jpg", "key.jpg", 
                "document.jpg", "book.jpg", "box.jpg", "gem.jpg"
            ));
        }
        
        return imageFiles;
    }
    
    private boolean isImageFile(String fileName) {
        String lowerCaseName = fileName.toLowerCase();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(lowerCaseName::endsWith);
    }
    
    public List<String> getAvailableItemImages() {
        List<String> imageFiles = new ArrayList<>();
        
        try {
            Resource resource = resourceLoader.getResource(ITEM_IMAGES_PATH);
            if (resource.exists() && resource.getFile().isDirectory()) {
                File imageDir = resource.getFile();
                File[] files = imageDir.listFiles();
                
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile() && isImageFile(file.getName())) {
                            imageFiles.add(file.getName());
                        }
                    }
                }
            }
        } catch (IOException e) {
            // 폴더가 없거나 접근할 수 없는 경우 빈 리스트 반환
        }
        
        // 기본 아이템 이미지들 추가 (실제 파일이 없어도 옵션으로 제공)
        if (imageFiles.isEmpty()) {
            imageFiles.addAll(Arrays.asList(
                "key.png", "potion.png", "coin.png", "gem.png", 
                "scroll.png", "book.png", "sword.png", "shield.png",
                "ring.png", "necklace.png", "crystal.png", "wand.png"
            ));
        }
        
        return imageFiles;
    }
    
    public String getImagePath(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        return "/images/" + fileName;
    }
    
    public String getItemImagePath(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        return "/images/items/" + fileName;
    }
}