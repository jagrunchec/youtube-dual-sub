package com.dualsub.repository;

import com.dualsub.model.SupportMessage;
import com.dualsub.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {
    List<SupportMessage> findByUserOrderByCreatedAtDesc(User user);
    List<SupportMessage> findAllByOrderByCreatedAtDesc();
    void deleteByUser_Id(Long userId);
}
