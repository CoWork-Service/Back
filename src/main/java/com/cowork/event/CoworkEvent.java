package com.cowork.event;

import com.cowork.cohort.Department;
import com.cowork.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "cowork_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CoworkEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cohort_id", nullable = false)
    private Long cohortId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EventStatus status = EventStatus.PLANNING;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(length = 200)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "lead_department", length = 20)
    private Department leadDepartment;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    private List<String> organizers;

    @Column
    private Long budget;

    @Column(name = "cover_color", length = 20)
    private String coverColor;

    @Column(name = "created_by")
    private Long createdBy;

    public void update(String name, String category, EventStatus status, String description,
                       LocalDate startDate, LocalDate endDate, String location, Department leadDepartment,
                       List<String> organizers, Long budget, String coverColor) {
        this.name = name;
        this.category = category;
        this.status = status;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        this.location = location;
        this.leadDepartment = leadDepartment;
        this.organizers = organizers;
        this.budget = budget;
        this.coverColor = coverColor;
    }
}
