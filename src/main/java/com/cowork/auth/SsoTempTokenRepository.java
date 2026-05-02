package com.cowork.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SsoTempTokenRepository extends JpaRepository<SsoTempToken, Long> {
    Optional<SsoTempToken> findByToken(String token);
    void deleteByToken(String token);
}