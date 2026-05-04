package com.cowork.asset;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 대여 기록(RentalRecord) 엔티티
 *
 * 역할:
 *   자산(Asset) 의 대여/반납 이력을 기록한다.
 *   누가, 언제, 몇 개를, 언제까지 빌렸는지 추적할 수 있다.
 *
 * 관계:
 *   - RentalRecord N : 1 Asset (asset_id FK)
 *
 * 상태 구분:
 *   - returnedAt == null  : 현재 대여 중
 *   - returnedAt != null  : 반납 완료
 *
 * 사용 시점:
 *   - 대여 시 : AssetService.rentAsset() 에서 레코드 생성.
 *   - 반납 시 : AssetService.returnAsset() 에서 returnAsset() 호출.
 *   - 이력 조회 : AssetService.getRentalHistory() 에서 asset_id 로 전체 이력 반환.
 */
@Entity
@Table(name = "rental_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RentalRecord {

    /** PK: auto increment */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 대여된 자산 ID (assets.id 참조) */
    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    /** 대여자 이름 */
    @Column(name = "borrower_name", nullable = false, length = 50)
    private String borrowerName;

    /** 대여자 학번 (선택, 학생 명부와 연결용) */
    @Column(name = "student_id", length = 20)
    private String studentId;

    /** 대여자 연락처 (선택) */
    @Column(length = 50)
    private String contact;

    /** 대여 시작 일시 */
    @Column(name = "rented_at", nullable = false)
    private LocalDateTime rentedAt;

    /** 반납 예정 일시 (연체 여부 계산 기준) */
    @Column(name = "due_at", nullable = false)
    private LocalDateTime dueAt;

    /** 실제 반납 일시 (반납 전까지는 null) */
    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    /** 대여 수량 (기본값: 1) */
    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    /** 대여 관련 메모 (손상 이력, 특이사항 등) */
    @Column(columnDefinition = "TEXT")
    private String note;

    /** 레코드 생성 일시 (변경 불가) */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** JPA 최초 저장 직전 — createdAt 초기화 */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 반납 처리
     *
     * 동작: returnedAt 을 현재 시각으로 설정하여 반납 완료 상태로 전환.
     * 사용 시점: AssetService.returnAsset() 에서 호출.
     *            이후 연결된 Asset.increaseAvailable() 도 함께 호출된다.
     */
    public void returnAsset() {
        this.returnedAt = LocalDateTime.now();
    }

    /**
     * 반납 완료 여부 확인
     *
     * @return true 이면 이미 반납 완료된 기록
     */
    public boolean isReturned() {
        return this.returnedAt != null;
    }
}
