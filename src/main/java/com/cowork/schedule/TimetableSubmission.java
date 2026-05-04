package com.cowork.schedule;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 타임테이블 응답(TimetableSubmission) 엔티티
 *
 * 역할:
 *   참여자(TimetableParticipant) 가 선택한 가능 시간 슬롯 목록을 저장한다.
 *   각 슬롯은 "날짜 시간" 형식의 문자열(예: "2025-03-10 09:00") 로 직렬화되어 JSON 배열로 보관된다.
 *
 * 관계:
 *   - TimetableSubmission N : 1 Timetable       (timetable_id FK)
 *   - TimetableSubmission N : 1 TimetableParticipant (participant_id FK)
 *
 * 사용 시점:
 *   - 참여자가 POST /api/timetables/{id}/respond 를 호출하면 이 레코드가 생성 또는 업데이트된다.
 *   - GET /api/timetables/{id}/results 에서 모든 응답을 집계해 슬롯별 참여 인원수를 반환한다.
 *
 * 슬롯 직렬화 형식:
 *   "2025-03-10 09:00" → date 부분과 time 부분을 공백으로 구분.
 *   SlotResponse.of() 에서 split(" ", 2) 로 역직렬화.
 */
@Entity
@Table(name = "timetable_responses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TimetableSubmission {

    /** PK: auto increment */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 응답 대상 타임테이블 ID */
    @Column(name = "timetable_id", nullable = false)
    private Long timetableId;

    /** 응답한 참여자 ID (timetable_participants.id 참조) */
    @Column(name = "participant_id", nullable = false)
    private Long participantId;

    /**
     * 참여자가 선택한 가능 시간 슬롯 목록 (JSON 배열)
     * 예: ["2025-03-10 09:00", "2025-03-10 09:30", "2025-03-11 14:00"]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_slots", nullable = false, columnDefinition = "JSON")
    private List<String> selectedSlots;

    /** 응답 제출 일시 (최초 제출 또는 마지막 수정 일시) */
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    /** JPA 최초 저장 직전 — submittedAt 초기화 */
    @PrePersist
    protected void onCreate() {
        this.submittedAt = LocalDateTime.now();
    }

    /**
     * 선택 슬롯 재제출 (수정)
     *
     * 동작: selectedSlots 를 새 목록으로 교체하고 submittedAt 을 현재 시각으로 갱신.
     * 사용 시점: 이미 응답한 참여자가 다시 응답을 수정할 때 ScheduleService.respond() 에서 호출.
     *
     * @param selectedSlots 새로 선택한 가능 시간 슬롯 목록
     */
    public void updateSelectedSlots(List<String> selectedSlots) {
        this.selectedSlots = selectedSlots;
        this.submittedAt = LocalDateTime.now();
    }
}
