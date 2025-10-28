package com.findguni.service;

import com.findguni.model.Item;
import com.findguni.repository.ItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ItemService {
    
    @Autowired
    private ItemRepository itemRepository;
    
    public List<Item> getItemsByPageId(String pageId) {
        return itemRepository.findByPageId(pageId);
    }
    
    public Optional<Item> getItemById(String id) {
        return itemRepository.findById(id);
    }
    
    public List<Item> getAllItems() {
        return itemRepository.findAll();
    }
    
    public Item saveItem(Item item) {
        return itemRepository.save(item);
    }
    
    public void deleteItem(String id) {
        itemRepository.deleteById(id);
    }
}