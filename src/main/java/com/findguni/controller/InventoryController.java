package com.findguni.controller;

import com.findguni.model.Item;
import com.findguni.service.InventoryService;
import com.findguni.service.ItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/api/inventory")
public class InventoryController {
    
    @Autowired
    private InventoryService inventoryService;
    
    @Autowired
    private ItemService itemService;
    
    @GetMapping
    @ResponseBody
    public ResponseEntity<List<Item>> getInventoryItems(HttpSession session) {
        List<Item> inventoryItems = inventoryService.getInventoryItems(session);
        return ResponseEntity.ok(inventoryItems);
    }
    
    @GetMapping("/item/{id}")
    @ResponseBody
    public ResponseEntity<Item> getItemDetails(@PathVariable String id, HttpSession session) {
        if (!inventoryService.hasItem(session, id)) {
            return ResponseEntity.notFound().build();
        }
        
        Optional<Item> item = itemService.getItemById(id);
        if (item.isPresent()) {
            return ResponseEntity.ok(item.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}