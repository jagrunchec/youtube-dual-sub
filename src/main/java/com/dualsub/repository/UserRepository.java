package com.dualsub.repository;

import com.dualsub.model.Role;
import com.dualsub.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findAllByOrderByCreatedAtDesc();
    long countByRole(Role role);
    long countByActive(boolean active);
}
