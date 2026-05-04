package com.cowork.auth;

import com.cowork.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Refresh Token 엔티티
 *
 * 역할:
 *   JWT 인증 방식에서 Access Token 갱신에 사용하는 Refresh Token 을 DB에 저장한다.
 *   한 사용자가 여러 기기에서 로그인할 경우 복수의 토큰이 존재할 수 있다.
 *
 * 동작 흐름:
 *   1. 로그인 성공 시 Refresh Token 을 생성해 이 테이블에 저장.
 *   2. 클라이언트가 Access Token 만료 후 /api/auth/refresh 를 호출하면
 *      저장된 토큰과 대조해 유효성 검증 후 새 토큰 쌍을 발급.
 *   3. 로그아웃 시 해당 사용자의 토큰 레코드를 삭제 → 재사용 방지.
 *
 * 사용 시점:
 *   - AuthService.login()    : 토큰 생성 및 저장
 *   - AuthService.refresh()  : 토큰 조회 및 유효성 검사
 *   - AuthService.logout()   : 토큰 삭제
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RefreshToken {

    /** PK: auto increment */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 토큰 소유자
     * - 지연 로딩(LAZY): 토큰 검증 시 사용자 정보가 필요한 시점에만 쿼리.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Refresh Token 문자열 (JWT 또는 랜덤 UUID)
     * - unique: 같은 토큰이 중복으로 저장되지 않도록 보장.
     */
    @Column(nullable = false, unique = true, length = 500)
    private String token;

    /** 토큰 만료 일시 — 이 시각 이후에는 사용 불가 */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** 토큰 발급(생성) 일시 (변경 불가) */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** JPA 최초 저장 직전 — createdAt 초기화 */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 토큰 만료 여부 확인
     *
     * 동작: 현재 시각이 expiresAt 을 지났으면 true 반환.
     * 사용 시점: AuthService.refresh() 에서 토큰 갱신 전에 만료 여부를 먼저 검사.
     *
     * @return true 이면 만료된 토큰 (사용 불가)
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }
}
