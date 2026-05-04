package com.cowork.event;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 행사 사진(EventPhoto) 엔티티
 *
 * 역할:
 *   행사(CoworkEvent) 에 업로드된 사진의 메타정보를 저장한다.
 *   실제 이미지 파일은 스토리지에 저장되며, 이 엔티티는 경로와 설명 정보만 보관한다.
 *
 * 관계:
 *   - EventPhoto N : 1 CoworkEvent  (event_id FK)
 *
 * 사용 시점:
 *   - 행사 진행 중 또는 후 사진 추가: POST /api/events/{id}/photos (multipart)
 *   - 사진 삭제: DELETE /api/events/{id}/photos/{photoId}
 *   - 행사 상세 조회 시 photos 리스트로 반환됨.
 */
@Entity
@Table(name = "event_photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EventPhoto {

    /** PK: auto increment */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 행사 ID */
    @Column(name = "event_id", nullable = false)
    private Long eventId;

    /** 스토리지 내 파일 경로 (예: "events/2025/photo_001.jpg") */
    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    /** 사진 설명/캡션 (선택, 최대 500자) */
    @Column(length = 500)
    private String caption;

    /**
     * 사진 태그 (분류 레이블)
     * - 예: "준비", "현장", "결과", "기타"
     * - 기본값: "기타"
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String tag = "기타";

    /** 업로드한 사용자 ID */
    @Column(name = "uploaded_by")
    private Long uploadedBy;

    /** 업로드 일시 (변경 불가) */
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    /** JPA 최초 저장 직전 — uploadedAt 초기화 */
    @PrePersist
    protected void onCreate() {
        this.uploadedAt = LocalDateTime.now();
    }
}
