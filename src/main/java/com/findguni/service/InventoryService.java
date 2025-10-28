package com.findguni.service;

import com.findguni.model.Item;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpSession;
import java.util.*;

@Service
public class InventoryService {
    
    private static final String INVENTORY_SESSION_KEY = "userInventory";
    
    @Autowired
    private ItemService itemService;
    
    @SuppressWarnings("unchecked")
    public Set<String> getInventory(HttpSession session) {
        Set<String> inventory = (Set<String>) session.getAttribute(INVENTORY_SESSION_KEY);
        if (inventory == null) {
            inventory = new HashSet<>();
            session.setAttribute(INVENTORY_SESSION_KEY, inventory);
        }
        return inventory;
    }
    
    public boolean addItem(HttpSession session, String itemId) {
        Set<String> inventory = getInventory(session);
        boolean added = inventory.add(itemId);
        session.setAttribute(INVENTORY_SESSION_KEY, inventory);
        return added;
    }
    
    public boolean hasItem(HttpSession session, String itemId) {
        Set<String> inventory = getInventory(session);
        return inventory.contains(itemId);
    }
    
    public List<Item> getInventoryItems(HttpSession session) {
        Set<String> inventoryIds = getInventory(session);
        List<Item> items = new ArrayList<>();
        
        for (String itemId : inventoryIds) {
            itemService.getItemById(itemId).ifPresent(items::add);
        }
        
        return items;
    }
    
    public boolean removeItem(HttpSession session, String itemId) {
        Set<String> inventory = getInventory(session);
        boolean removed = inventory.remove(itemId);
        session.setAttribute(INVENTORY_SESSION_KEY, inventory);
        return removed;
    }
    
    public void addItems(HttpSession session, List<String> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) return;
        
        Set<String> inventory = getInventory(session);
        for (String itemId : itemIds) {
            inventory.add(itemId);
        }
        session.setAttribute(INVENTORY_SESSION_KEY, inventory);
    }
    
    public void removeItems(HttpSession session, List<String> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) return;
        
        Set<String> inventory = getInventory(session);
        for (String itemId : itemIds) {
            inventory.remove(itemId);
        }
        session.setAttribute(INVENTORY_SESSION_KEY, inventory);
    }
    
    public void clearInventory(HttpSession session) {
        session.removeAttribute(INVENTORY_SESSION_KEY);
    }
}