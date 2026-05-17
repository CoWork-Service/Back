package com.cowork.budget;

import com.cowork.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * 지출 내역(Expense) 엔티티
 *
 * 역할:
 *   코호트의 예산 지출 내역을 기록한다.
 *   날짜·부서·분류·거래처·금액·결제 수단을 추적하여 예산 관리 및 정산에 활용한다.
 *   영수증 파일을 첨부할 수 있으며, 특정 행사(event_id) 와 연결할 수도 있다.
 *
 * 관계:
 *   - cohort_id 로 코호트와 연결
 *   - event_id 로 특정 행사와 연결 가능 (선택)
 *
 * 사용 시점:
 *   - 지출 발생 시 기록: POST /api/expenses (multipart, 영수증 파일 포함 가능)
 *   - 기간·부서·분류 필터링 목록 조회: GET /api/expenses
 *   - 전체 요약 통계 조회: GET /api/expenses/summary (총액, 부서별 집계 등)
 */
@Entity
@Table(name = "expenses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Expense extends BaseEntity {

    /** PK: auto increment */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 코호트 ID */
    @Column(name = "cohort_id", nullable = false)
    private Long cohortId;

    /** 지출 날짜 */
    @Column(nullable = false)
    private LocalDate date;

    /** 지출 부서 (조직별 커스텀 부서명) */
    @Column(nullable = false, length = 100)
    private String department;

    /** 지출 분류 (예: "식비", "인쇄비", "교통비") */
    @Column(nullable = false, length = 100)
    private String category;

    /** 거래처/공급업체 이름 (예: "GS편의점", "이마트") */
    @Column(nullable = false, length = 100)
    private String vendor;

    /** 지출 상세 설명 (선택) */
    @Column(length = 500)
    private String description;

    /** 지출 금액 (원화) */
    @Column(nullable = false)
    private Long amount;

    /** 결제 수단 (예: "법인카드", "개인카드", "현금") */
    @Column(name = "payment_method", nullable = false, length = 50)
    private String paymentMethod;

    /** 영수증 파일의 스토리지 경로 (선택) */
    @Column(name = "receipt_storage_path", length = 500)
    private String receiptStoragePath;

    /** 관리자 메모 (자유 입력) */
    @Column(columnDefinition = "TEXT")
    private String note;

    /** 연결된 행사 ID (행사 지출인 경우 설정, 선택) */
    @Column(name = "event_id")
    private Long eventId;

    /**
     * 지출 내역 수정
     *
     * 동작: 날짜·부서·분류·거래처·설명·금액·결제수단·메모·행사ID 를 한꺼번에 업데이트.
     * 사용 시점: ExpenseService.updateExpense() 에서 호출 (PUT /api/expenses/{id}).
     */
    public void update(LocalDate date, String department, String category, String vendor,
                       String description, Long amount, String paymentMethod, String note, Long eventId) {
        this.date = date;
        this.department = department;
        this.category = category;
        this.vendor = vendor;
        this.description = description;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.note = note;
        this.eventId = eventId;
    }

    /**
     * 영수증 경로 설정
     *
     * 동작: 업로드된 영수증 파일의 저장 경로를 기록.
     * 사용 시점: ExpenseService 에서 MultipartFile 업로드 후 경로를 엔티티에 반영.
     *
     * @param path 스토리지 내 파일 경로
     */
    public void setReceiptPath(String path) {
        this.receiptStoragePath = path;
    }
}
