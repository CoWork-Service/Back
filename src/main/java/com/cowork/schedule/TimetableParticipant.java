package com.cowork.schedule;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 타임테이블 참여자(TimetableParticipant) 엔티티
 *
 * 역할:
 *   일정 조율표(Timetable)에 참여하도록 등록된 사람들의 목록을 관리한다.
 *   관리자가 미리 이름을 등록해두면, 해당 참여자가 링크로 접속해 가능 시간을 제출한다.
 *
 * 관계:
 *   - TimetableParticipant N : 1 Timetable  (timetable_id FK)
 *   - TimetableParticipant 1 : N TimetableSubmission
 *
 * 상태:
 *   - responded = false : 아직 가능 시간을 제출하지 않음
 *   - responded = true  : 제출 완료 (markResponded() 호출 후)
 *
 * 사용 시점:
 *   - 타임테이블 생성 시 participants 목록을 함께 등록.
 *   - 참여자가 응답을 제출하면 markResponded() 를 호출해 응답 여부 표시.
 */
@Entity
@Table(name = "timetable_participants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TimetableParticipant {

    /** PK: auto increment */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 타임테이블 ID */
    @Column(name = "timetable_id", nullable = false)
    private Long timetableId;

    /** 참여자 이름 (응답 제출 시 이름으로 본인 확인) */
    @Column(nullable = false, length = 50)
    private String name;

    /**
     * 응답 완료 여부
     * - false: 아직 가능 시간 미제출
     * - true : 제출 완료
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean responded = false;

    /** 참여자 등록 일시 (변경 불가) */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** JPA 최초 저장 직전 — createdAt 초기화 */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 응답 완료 표시
     *
     * 동작: responded 를 true 로 변경.
     * 사용 시점: ScheduleService.respond() 에서 가능 시간 제출이 완료되었을 때 호출.
     */
    public void markResponded() {
        this.responded = true;
    }
}
