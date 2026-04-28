package com.cowork.student;

import com.cowork.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "students",
       uniqueConstraints = @UniqueConstraint(columnNames = {"cohort_id", "student_number"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Student extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cohort_id", nullable = false)
    private Long cohortId;

    @Column(name = "student_number", nullable = false, length = 20)
    private String studentNumber;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 100)
    private String department;

    @Column
    private Integer grade;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 10)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(columnDefinition = "TEXT")
    private String note;

    public void update(String studentNumber, String name, String department, Integer grade, String note) {
        this.studentNumber = studentNumber;
        this.name = name;
        this.department = department;
        this.grade = grade;
        this.note = note;
    }

    public void pay() {
        this.paymentStatus = PaymentStatus.PAID;
        this.paidAt = LocalDateTime.now();
    }

    public void unpay() {
        this.paymentStatus = PaymentStatus.UNPAID;
        this.paidAt = null;
    }

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
