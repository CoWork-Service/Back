package com.cowork.auth;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "sso_temp_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SsoTempToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String token;

    @Column(name = "student_id", nullable = false, length = 20)
    private String studentId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 100)
    private String department;

    @Column(length = 200)
    private String email;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }
}