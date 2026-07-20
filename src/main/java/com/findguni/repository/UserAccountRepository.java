package com.findguni.repository;

import com.findguni.model.Role;
import com.findguni.model.AccountStatus;
import com.findguni.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<UserAccount> findAllByRoleOrderByCreatedAtDesc(Role role);
    long countByRole(Role role);
    long countByRoleAndStatus(Role role, AccountStatus status);
}
