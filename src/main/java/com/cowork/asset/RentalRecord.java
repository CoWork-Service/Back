package com.cowork.asset;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "rental_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RentalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "borrower_name", nullable = false, length = 50)
    private String borrowerName;

    @Column(name = "student_id", length = 20)
    private String studentId;

    @Column(length = 50)
    private String contact;

    @Column(name = "rented_at", nullable = false)
    private LocalDateTime rentedAt;

    @Column(name = "due_at", nullable = false)
    private LocalDateTime dueAt;

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void returnAsset() {
        this.returnedAt = LocalDateTime.now();
    }

    public boolean isReturned() {
        return this.returnedAt != null;
    }
}
