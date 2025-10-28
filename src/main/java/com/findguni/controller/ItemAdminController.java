package com.findguni.controller;

import com.findguni.model.Item;
import com.findguni.service.ItemService;
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
    
    @GetMapping
    public String showItemList(Model model) {
        List<Item> items = itemService.getAllItems();
        model.addAttribute("items", items);
        return "admin/item-list";
    }
    
    @GetMapping("/new")
    public String showItemForm(Model model) {
        model.addAttribute("item", new Item());
        return "admin/item-form";
    }
    
    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable String id, Model model) {
        Optional<Item> item = itemService.getItemById(id);
        if (item.isPresent()) {
            model.addAttribute("item", item.get());
            return "admin/item-form";
        }
        return "redirect:/admin/items";
    }
    
    @PostMapping("/save")
    public String saveItem(@ModelAttribute Item item) {
        itemService.saveItem(item);
        return "redirect:/admin/items";
    }
    
    @PostMapping("/delete/{id}")
    public String deleteItem(@PathVariable String id) {
        itemService.deleteItem(id);
        return "redirect:/admin/items";
    }
}