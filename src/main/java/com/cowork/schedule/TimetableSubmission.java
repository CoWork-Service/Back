package com.cowork.schedule;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "timetable_responses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TimetableSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "timetable_id", nullable = false)
    private Long timetableId;

    @Column(name = "participant_id", nullable = false)
    private Long participantId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_slots", nullable = false, columnDefinition = "JSON")
    private List<String> selectedSlots;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @PrePersist
    protected void onCreate() {
        this.submittedAt = LocalDateTime.now();
    }

    public void updateSelectedSlots(List<String> selectedSlots) {
        this.selectedSlots = selectedSlots;
        this.submittedAt = LocalDateTime.now();
    }
}
