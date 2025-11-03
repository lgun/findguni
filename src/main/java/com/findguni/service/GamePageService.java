package com.findguni.service;

import com.findguni.model.GamePage;
import com.findguni.repository.GamePageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.util.*;

@Service
public class GamePageService {
    
    @Autowired
    private GamePageRepository gamePageRepository;
    
    // 자동으로 기본 페이지를 생성하지 않음 - 관리자가 필요시 수동으로 생성
    // @PostConstruct 제거하여 서버 시작 시 자동 실행 방지
    
    public GamePage getPage(String id) {
        return gamePageRepository.findById(id).orElse(null);
    }
    
    public List<GamePage> getAllPages() {
        return gamePageRepository.findAll();
    }
    
    @Transactional
    public GamePage addPage(GamePage page) {
        return gamePageRepository.save(page);
    }
    
    @Transactional
    public GamePage updatePage(GamePage page) {
        return gamePageRepository.save(page);
    }
    
    @Transactional
    public void deletePage(String id) {
        gamePageRepository.deleteById(id);
    }
    
    public boolean pageExists(String id) {
        return gamePageRepository.existsById(id);
    }
}