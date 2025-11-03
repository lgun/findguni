package com.findguni.controller;

import com.findguni.model.Item;
import com.findguni.service.ItemService;
import com.findguni.service.ImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin/items")
public class ItemAdminController {
    
    @Autowired
    private ItemService itemService;
    
    @Autowired
    private ImageService imageService;
    
    @GetMapping
    public String showItemList(Model model) {
        List<Item> items = itemService.getAllItems();
        model.addAttribute("items", items);
        return "admin/item-list";
    }
    
    @GetMapping("/new")
    public String showItemForm(Model model) {
        model.addAttribute("item", new Item());
        model.addAttribute("availableItemImages", imageService.getAvailableItemImages());
        return "admin/item-form";
    }
    
    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable String id, Model model) {
        Optional<Item> item = itemService.getItemById(id);
        if (item.isPresent()) {
            model.addAttribute("item", item.get());
            model.addAttribute("availableItemImages", imageService.getAvailableItemImages());
            return "admin/item-form";
        }
        return "redirect:/admin/items";
    }
    
    @PostMapping("/save")
    public String saveItem(@ModelAttribute Item item, 
                          @RequestParam(required = false) String selectedItemImage) {
        // 선택된 이미지가 있으면 경로 설정
        if (selectedItemImage != null && !selectedItemImage.isEmpty()) {
            item.setImageUrl(imageService.getItemImagePath(selectedItemImage));
        }
        
        itemService.saveItem(item);
        return "redirect:/admin/items";
    }
    
    @PostMapping("/delete/{id}")
    public String deleteItem(@PathVariable String id) {
        itemService.deleteItem(id);
        return "redirect:/admin/items";
    }
}