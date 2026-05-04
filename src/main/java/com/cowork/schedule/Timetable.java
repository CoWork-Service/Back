package com.cowork.schedule;

import com.cowork.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 타임테이블(Timetable) 엔티티 — 일정 조율표
 *
 * 역할:
 *   구성원들의 가능 시간대를 수집해 최적 일정을 찾는 "When2meet" 스타일의 조율표.
 *   날짜 범위·시간 범위·슬롯 단위를 설정하면, 참여자들이 각자 가능한 슬롯을 선택한다.
 *
 * 관계:
 *   - Timetable 1 : N TimetableParticipant  (timetable_participants 테이블)
 *   - Timetable 1 : N TimetableSubmission   (timetable_responses 테이블)
 *   - cohort_id, event_id 로 코호트·행사와 연결
 *
 * 상태 흐름 (TimetableStatus):
 *   OPEN (응답 수집 중) ──마감→ CLOSED (결과 확정)
 *
 * 사용 시점:
 *   - 행사나 회의 날짜를 정할 때 생성.
 *   - 참여자들이 링크를 통해 가능 시간을 제출하면 결과에서 겹치는 슬롯을 집계.
 */
@Entity
@Table(name = "timetables")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Timetable extends BaseEntity {

    /** PK: auto increment */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 코호트 ID */
    @Column(name = "cohort_id", nullable = false)
    private Long cohortId;

    /** 조율표 제목 (예: "3월 정기총회 일정 조율") */
    @Column(nullable = false, length = 200)
    private String title;

    /** 조율표 설명 (참여자에게 안내할 내용, 선택) */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * 조율 대상 날짜 범위 시작
     * 예: 2025-03-10 ~ 2025-03-14 (5일간 조율)
     */
    @Column(name = "date_range_start", nullable = false)
    private LocalDate dateRangeStart;

    /** 조율 대상 날짜 범위 종료 */
    @Column(name = "date_range_end", nullable = false)
    private LocalDate dateRangeEnd;

    /**
     * 하루 중 조율 시간 범위 시작
     * 예: 09:00 ~ 22:00 (매일 9시~22시 슬롯 제공)
     */
    @Column(name = "time_range_start", nullable = false)
    private LocalTime timeRangeStart;

    /** 하루 중 조율 시간 범위 종료 */
    @Column(name = "time_range_end", nullable = false)
    private LocalTime timeRangeEnd;

    /**
     * 슬롯 단위 (분)
     * - 30 이면 30분 단위로 슬롯 생성.
     * - 60 이면 1시간 단위.
     */
    @Column(name = "slot_minutes", nullable = false)
    private Integer slotMinutes;

    /**
     * 현재 상태
     * - OPEN   : 응답 수집 중 (참여자 접속 가능)
     * - CLOSED : 마감 (결과만 조회 가능)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TimetableStatus status = TimetableStatus.OPEN;

    /** 생성자 사용자 ID */
    @Column(name = "created_by")
    private Long createdBy;

    /** 연결된 행사 ID (특정 행사의 일정 조율 시 설정, 선택) */
    @Column(name = "event_id")
    private Long eventId;

    /**
     * 타임테이블 정보 수정
     *
     * 동작: 제목·설명·날짜범위·시간범위·슬롯단위·행사ID 를 한꺼번에 업데이트.
     * 사용 시점: ScheduleService.updateTimetable() 에서 호출.
     */
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

    /**
     * 상태 변경 (OPEN ↔ CLOSED)
     *
     * 동작: 현재 상태를 새 상태로 변경.
     * 사용 시점: ScheduleService.updateStatus() 에서 호출 (PATCH /api/timetables/{id}/status).
     *
     * @param status 새 상태
     */
    public void updateStatus(TimetableStatus status) {
        this.status = status;
    }
}
