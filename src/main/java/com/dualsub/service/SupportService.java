package com.dualsub.service;

import com.dualsub.model.MessageStatus;
import com.dualsub.model.SupportMessage;
import com.dualsub.model.User;
import com.dualsub.repository.SupportMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SupportService {

    private final SupportMessageRepository repo;

    public SupportService(SupportMessageRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public SupportMessage send(User user, String subject, String body) {
        SupportMessage msg = new SupportMessage();
        msg.setUser(user);
        msg.setSubject(subject.trim());
        msg.setBody(body.trim());
        msg.setStatus(MessageStatus.OPEN);
        msg.setCreatedAt(LocalDateTime.now());
        return repo.save(msg);
    }

    public List<SupportMessage> getForUser(User user) {
        return repo.findByUserOrderByCreatedAtDesc(user);
    }

    public List<SupportMessage> getAll() {
        return repo.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public SupportMessage respond(Long messageId, String responseText, MessageStatus newStatus) {
        SupportMessage msg = repo.findById(messageId)
            .orElseThrow(() -> new IllegalArgumentException("Message introuvable : " + messageId));
        msg.setAdminResponse(responseText.trim());
        msg.setStatus(newStatus);
        msg.setRespondedAt(LocalDateTime.now());
        return repo.save(msg);
    }
}
