package com.dualsub.repository;

import com.dualsub.model.AccountToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountTokenRepository extends JpaRepository<AccountToken, String> {
    void deleteByUser_Id(Long userId);
    Optional<AccountToken> findByTokenAndTokenType(String token, AccountToken.TokenType type);
}
