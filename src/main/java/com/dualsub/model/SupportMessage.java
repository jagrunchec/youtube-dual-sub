package com.dualsub.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "support_messages")
public class SupportMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageStatus status = MessageStatus.OPEN;

    @Column(name = "admin_response", columnDefinition = "TEXT")
    private String adminResponse;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    public Long getId()                                { return id; }
    public void setId(Long id)                         { this.id = id; }

    public User getUser()                              { return user; }
    public void setUser(User user)                     { this.user = user; }

    public String getSubject()                         { return subject; }
    public void setSubject(String subject)             { this.subject = subject; }

    public String getBody()                            { return body; }
    public void setBody(String body)                   { this.body = body; }

    public MessageStatus getStatus()                   { return status; }
    public void setStatus(MessageStatus status)        { this.status = status; }

    public String getAdminResponse()                   { return adminResponse; }
    public void setAdminResponse(String adminResponse) { this.adminResponse = adminResponse; }

    public LocalDateTime getCreatedAt()                { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt)  { this.createdAt = createdAt; }

    public LocalDateTime getRespondedAt()              { return respondedAt; }
    public void setRespondedAt(LocalDateTime v)        { this.respondedAt = v; }
}
