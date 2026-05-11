package com.cowork.student;

import com.cowork.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 학생(Student) 엔티티
 *
 * 역할:
 *   코호트에 속한 학생 명단을 관리한다.
 *   User(로그인 계정)와는 별개로, 수강생·회원 명부처럼 순수하게 인원 정보만 저장한다.
 *   회비 납부 상태(paymentStatus) 를 함께 관리하여 정산/관리 업무를 지원한다.
 *
 * 관계:
 *   - (cohort_id, student_number) 복합 유니크 → 같은 기수에 동일 학번 중복 불가.
 *   - cohort_id 는 외래키 없이 Long 으로 보관 (cohorts 테이블 ID를 직접 참조).
 *
 * 사용 시점:
 *   - 관리자가 학생을 개별 등록하거나 엑셀 파일로 일괄 업로드할 때.
 *   - 회비 납부 상태를 단건/일괄로 변경할 때.
 */
@Entity
@Table(name = "students",
       uniqueConstraints = @UniqueConstraint(columnNames = {"cohort_id", "student_number"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Student extends BaseEntity {

    /** PK: auto increment */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 코호트 ID (cohorts.id 참조) */
    @Column(name = "cohort_id", nullable = false)
    private Long cohortId;

    /** 학번 또는 고유 식별 번호 (같은 코호트 내에서 유일) */
    @Column(name = "student_number", nullable = false, length = 20)
    private String studentNumber;

    /** 학생 이름 */
    @Column(nullable = false, length = 50)
    private String name;

    /** 소속 학과/전공 (선택) */
    @Column(length = 100)
    private String department;

    /** 학년 (1~4, 선택) */
    @Column
    private Integer grade;

    /**
     * 회비 납부 상태
     * - UNPAID : 미납 (기본값)
     * - PAID   : 납부 완료
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 10)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    /** 회비 납부 일시 (미납이면 null) */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /** 관리자 메모 (자유 입력) */
    @Column(columnDefinition = "TEXT")
    private String note;

    /**
     * 학생 기본 정보 수정
     *
     * 동작: 학번·이름·학과·학년·메모를 한꺼번에 업데이트한다.
     * 사용 시점: 관리자가 개별 학생 정보를 편집할 때 StudentService.updateStudent() 에서 호출.
     */
    public void update(String studentNumber, String name, String department, Integer grade, String note) {
        if (studentNumber != null) {
            this.studentNumber = studentNumber;
        }
        if (name != null) {
            this.name = name;
        }
        if (department != null) {
            this.department = department;
        }
        if (grade != null) {
            this.grade = grade;
        }
        if (note != null) {
            this.note = note;
        }
    }

    /**
     * 회비 납부 처리
     *
     * 동작: paymentStatus 를 PAID 로 변경하고 paidAt 을 현재 시각으로 설정.
     * 사용 시점: 단건 또는 일괄 납부 처리 시 StudentService 에서 호출.
     */
    public void pay() {
        this.paymentStatus = PaymentStatus.PAID;
        this.paidAt = LocalDateTime.now();
    }

    /**
     * 회비 납부 취소 (환불/오입력 정정)
     *
     * 동작: paymentStatus 를 UNPAID 로 되돌리고 paidAt 을 null 로 초기화.
     * 사용 시점: 납부 기록을 잘못 입력했을 때 수동 정정.
     */
    public void unpay() {
        this.paymentStatus = PaymentStatus.UNPAID;
        this.paidAt = null;
    }

    /**
     * 엑셀 업로드 시 납부 상태 일괄 적용
     *
     * 동작: paymentStatus 가 PAID 이면 pay() 처럼 처리하되 paidAt 을 파라미터 값으로 사용.
     *       UNPAID 이면 paidAt 을 null 로 초기화.
     * 사용 시점: 일괄 업로드(import) 에서 파일에 기재된 납부 정보를 그대로 반영할 때.
     *
     * @param paymentStatus 납부 상태
     * @param paidAt        납부 일시 (파일에 기재된 값, 없으면 null → 현재 시각으로 대체)
     */
    public void applyPaymentStatus(PaymentStatus paymentStatus, LocalDateTime paidAt) {
        if (paymentStatus == PaymentStatus.PAID) {
            this.paymentStatus = PaymentStatus.PAID;
            this.paidAt = paidAt != null ? paidAt : LocalDateTime.now();
            return;
        }
        this.paymentStatus = PaymentStatus.UNPAID;
        this.paidAt = null;
    }
}
