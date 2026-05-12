package com.dualsub.repository;

import com.dualsub.model.SecurityQuestion;
import com.dualsub.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SecurityQuestionRepository extends JpaRepository<SecurityQuestion, Long> {
    List<SecurityQuestion> findByUserOrderByQuestionOrder(User user);
    void deleteByUser(User user);
}
