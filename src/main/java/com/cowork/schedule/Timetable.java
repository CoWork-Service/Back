package com.cowork.schedule;

import com.cowork.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "timetables")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Timetable extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cohort_id", nullable = false)
    private Long cohortId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "date_range_start", nullable = false)
    private LocalDate dateRangeStart;

    @Column(name = "date_range_end", nullable = false)
    private LocalDate dateRangeEnd;

    @Column(name = "time_range_start", nullable = false)
    private LocalTime timeRangeStart;

    @Column(name = "time_range_end", nullable = false)
    private LocalTime timeRangeEnd;

    @Column(name = "slot_minutes", nullable = false)
    private Integer slotMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TimetableStatus status = TimetableStatus.OPEN;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "event_id")
    private Long eventId;

    public void update(String title, String description, LocalDate dateRangeStart, LocalDate dateRangeEnd,
                       LocalTime timeRangeStart, LocalTime timeRangeEnd, Integer slotMinutes, Long eventId) {
        this.title = title;
        this.description = description;
        this.dateRangeStart = dateRangeStart;
        this.dateRangeEnd = dateRangeEnd;
        this.timeRangeStart = timeRangeStart;
        this.timeRangeEnd = timeRangeEnd;
        this.slotMinutes = slotMinutes;
        this.eventId = eventId;
    }

    public void updateStatus(TimetableStatus status) {
        this.status = status;
    }
}
