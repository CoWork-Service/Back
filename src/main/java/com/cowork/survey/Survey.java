package com.cowork.survey;

import com.cowork.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "surveys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Survey extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cohort_id", nullable = false)
    private Long cohortId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SurveyStatus status = SurveyStatus.DRAFT;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "event_id")
    private Long eventId;

    public void update(String title, String description, Long eventId) {
        this.title = title;
        this.description = description;
        this.eventId = eventId;
    }

    public void updateStatus(SurveyStatus status) {
        this.status = status;
    }
}
