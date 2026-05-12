package com.dualsub.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "account_tokens")
public class AccountToken {

    public enum TokenType { EMAIL_VERIFICATION, PASSWORD_RESET }

    @Id
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", nullable = false)
    private TokenType tokenType;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    // ── Getters / Setters ─────────────────────────────────────────

    public String getToken()                   { return token; }
    public void   setToken(String token)       { this.token = token; }

    public User getUser()                      { return user; }
    public void setUser(User user)             { this.user = user; }

    public TokenType getTokenType()            { return tokenType; }
    public void setTokenType(TokenType t)      { this.tokenType = t; }

    public LocalDateTime getExpiresAt()        { return expiresAt; }
    public void setExpiresAt(LocalDateTime t)  { this.expiresAt = t; }

    public LocalDateTime getUsedAt()           { return usedAt; }
    public void setUsedAt(LocalDateTime t)     { this.usedAt = t; }

    public boolean isExpired()  { return LocalDateTime.now().isAfter(expiresAt); }
    public boolean isUsed()     { return usedAt != null; }
}
