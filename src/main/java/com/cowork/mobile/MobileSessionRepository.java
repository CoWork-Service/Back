package com.cowork.mobile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MobileSessionRepository extends JpaRepository<MobileSession, Long> {

    Optional<MobileSession> findBySessionToken(String sessionToken);
}
