package com.findguni.repository;

import com.findguni.model.GamePage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GamePageRepository extends JpaRepository<GamePage, String> {
    
    Optional<GamePage> findById(String id);
    
    boolean existsById(String id);
}