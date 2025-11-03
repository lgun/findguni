package com.findguni.service;

import com.findguni.model.Item;
import com.findguni.repository.ItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Collections;

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
        List<Item> items = itemRepository.findAll();
        Collections.reverse(items);  // DB 순서를 뒤집어서 최신순으로
        return items;
    }
    
    public Item saveItem(Item item) {
        return itemRepository.save(item);
    }
    
    public void deleteItem(String id) {
        itemRepository.deleteById(id);
    }
}