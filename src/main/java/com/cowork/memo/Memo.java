package com.cowork.memo;

import com.cowork.cohort.Department;
import com.cowork.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "memos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Memo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cohort_id", nullable = false)
    private Long cohortId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Department department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MemoPriority priority = MemoPriority.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private MemoStatus status = MemoStatus.OPEN;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(nullable = false, length = 50)
    private String author;

    public void update(String title, String content, Department department, MemoPriority priority,
                       MemoStatus status, LocalDate dueDate) {
        this.title = title;
        this.content = content;
        this.department = department;
        this.priority = priority;
        this.status = status;
        this.dueDate = dueDate;
    }

    public void updateStatus(MemoStatus status) {
        this.status = status;
    }

    public void updatePriority(MemoPriority priority) {
        this.priority = priority;
    }
}
