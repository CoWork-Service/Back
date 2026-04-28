package com.cowork.mobile;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "mobile_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MobileSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_token", nullable = false, unique = true, length = 64)
    private String sessionToken;

    @Column(name = "cohort_id", nullable = false)
    private Long cohortId;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean used = false;

    @Column(name = "photo_path", length = 500)
    private String photoPath;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_data", columnDefinition = "JSON")
    private Map<String, Object> extraData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }

    public void complete(String photoPath, Map<String, Object> extraData) {
        this.used = true;
        this.photoPath = photoPath;
        this.extraData = extraData;
    }
}
