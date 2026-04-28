package com.cowork.budget;

import com.cowork.cohort.Department;
import com.cowork.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "expenses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Expense extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cohort_id", nullable = false)
    private Long cohortId;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Department department;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(nullable = false, length = 100)
    private String vendor;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "payment_method", nullable = false, length = 50)
    private String paymentMethod;

    @Column(name = "receipt_storage_path", length = 500)
    private String receiptStoragePath;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "event_id")
    private Long eventId;

    public void update(LocalDate date, Department department, String category, String vendor,
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

    public void setReceiptPath(String path) {
        this.receiptStoragePath = path;
    }
}
