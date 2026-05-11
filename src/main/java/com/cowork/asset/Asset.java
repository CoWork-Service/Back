package com.cowork.asset;

import com.cowork.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

/**
 * 자산(Asset) 엔티티
 *
 * 역할:
 *   코호트가 보유한 물품(노트북, 카메라, 빔프로젝터 등)을 관리한다.
 *   수량, 가용 수량, 위치, 대여 상태를 추적하여 물품 관리 및 대여 운영을 지원한다.
 *
 * 관계:
 *   - Asset 1 : N RentalRecord  (rental_records 테이블의 asset_id FK)
 *   - cohort_id 로 코호트와 연결 (FK 없이 Long 으로 보관)
 *
 * 상태 흐름 (AssetStatus):
 *   AVAILABLE (대여 가능) ──대여→ RENTED (전부 대여 중) ──반납→ AVAILABLE
 *   MAINTENANCE / DISPOSED 는 별도로 설정 가능
 *
 * 사용 시점:
 *   - 물품 등록·수정·삭제: AssetService.createAsset / updateAsset / deleteAsset
 *   - 대여 시 decreaseAvailable(), 반납 시 increaseAvailable() 호출로 수량·상태 자동 갱신.
 */
@Entity
@Table(name = "assets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Asset extends BaseEntity {

    /** PK: auto increment */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 코호트 ID */
    @Column(name = "cohort_id", nullable = false)
    private Long cohortId;

    /** 물품명 (예: "맥북 프로 16인치") */
    @Column(nullable = false, length = 200)
    private String name;

    /** 물품 분류 (예: "전자기기", "사무용품") */
    @Column(length = 100)
    private String category;

    /**
     * 태그 목록 (JSON 배열로 저장)
     * - 예: ["중요", "공용", "외부대여가능"]
     * - 검색 필터링에 활용 가능.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    private List<String> tags;

    /** 물품 사진의 저장 경로 (없으면 null) */
    @Column(name = "photo_storage_path", length = 500)
    private String photoStoragePath;

    /** 총 보유 수량 (기본값: 1) */
    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    /**
     * 현재 대여 가능한 수량
     * - 대여 시 감소, 반납 시 증가.
     * - 0 이 되면 상태가 자동으로 RENTED 로 변경.
     */
    @Column(name = "available_quantity", nullable = false)
    @Builder.Default
    private Integer availableQuantity = 1;

    /** 구매 금액 (원화, 선택) */
    @Column(name = "purchase_price")
    private Long purchasePrice;

    /** 보관 위치 (예: "총학 창고 A-2") */
    @Column(length = 200)
    private String location;

    /**
     * 자산 상태
     * - AVAILABLE   : 대여 가능
     * - RENTED      : 전량 대여 중
     * - MAINTENANCE : 수리/점검 중
     * - DISPOSED    : 폐기
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AssetStatus status = AssetStatus.AVAILABLE;

    /** 자산 설명 (자유 입력) */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * 자산 기본 정보 수정
     *
     * 동작: 이름·분류·태그·수량·가격·위치·상태·설명을 한꺼번에 업데이트.
     * 사용 시점: AssetService.updateAsset() 에서 호출.
     */
    public void update(String name, String category, List<String> tags, Integer quantity,
                       Long purchasePrice, String location, AssetStatus status, String description) {
        if (name != null) {
            this.name = name;
        }
        if (category != null) {
            this.category = category;
        }
        if (tags != null) {
            this.tags = tags;
        }
        if (quantity != null) {
            int rentedQuantity = Math.max(0, this.quantity - this.availableQuantity);
            this.quantity = quantity;
            this.availableQuantity = Math.max(0, quantity - rentedQuantity);
            if (this.availableQuantity == 0) {
                this.status = AssetStatus.RENTED;
            } else if (this.status == AssetStatus.RENTED) {
                this.status = AssetStatus.AVAILABLE;
            }
        }
        if (purchasePrice != null) {
            this.purchasePrice = purchasePrice;
        }
        if (location != null) {
            this.location = location;
        }
        if (status != null) {
            this.status = status;
        }
        if (description != null) {
            this.description = description;
        }
    }

    /**
     * 사진 경로 설정
     *
     * 동작: 업로드된 사진 파일의 저장 경로를 기록.
     * 사용 시점: AssetService 에서 MultipartFile 업로드 후 경로를 엔티티에 반영.
     */
    public void setPhotoPath(String path) {
        this.photoStoragePath = path;
    }

    /**
     * 대여 가능 수량 감소 (대여 시 호출)
     *
     * 동작: availableQuantity 를 qty 만큼 줄인다 (최소 0까지).
     *       0이 되면 status 를 RENTED 로 자동 변경.
     * 사용 시점: AssetService.rentAsset() 에서 대여 처리 시 호출.
     *
     * @param qty 대여 수량
     */
    public void decreaseAvailable(int qty) {
        this.availableQuantity = Math.max(0, this.availableQuantity - qty);
        if (this.availableQuantity == 0) {
            this.status = AssetStatus.RENTED;
        }
    }

    /**
     * 대여 가능 수량 증가 (반납 시 호출)
     *
     * 동작: availableQuantity 를 qty 만큼 늘린다 (최대 quantity 까지).
     *       RENTED 상태였다가 가용 수량이 생기면 AVAILABLE 로 자동 복원.
     * 사용 시점: AssetService.returnAsset() 에서 반납 처리 시 호출.
     *
     * @param qty 반납 수량
     */
    public void increaseAvailable(int qty) {
        this.availableQuantity = Math.min(this.quantity, this.availableQuantity + qty);
        if (this.availableQuantity > 0 && this.status == AssetStatus.RENTED) {
            this.status = AssetStatus.AVAILABLE;
        }
    }
}
