package com.cowork.event;

import com.cowork.cohort.Department;
import com.cowork.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.List;

/**
 * 행사(CoworkEvent) 엔티티
 *
 * 역할:
 *   코호트가 주관하는 행사(축제, 발표회, 워크숍 등)를 관리한다.
 *   행사에는 사진, 지출 내역, 설문, 일정 조율표 등 다양한 하위 데이터가 연결된다.
 *
 * 관계:
 *   - CoworkEvent 1 : N EventPhoto        (event_photos 테이블)
 *   - CoworkEvent 1 : N Expense           (expenses 테이블의 event_id)
 *   - CoworkEvent 1 : N Survey            (surveys 테이블의 event_id)
 *   - CoworkEvent 1 : N Timetable         (timetables 테이블의 event_id)
 *   - cohort_id 로 코호트와 연결
 *
 * 상태 흐름 (EventStatus):
 *   PLANNING (기획 중) → IN_PROGRESS (진행 중) → COMPLETED (완료) / CANCELLED (취소)
 *
 * 사용 시점:
 *   - 새 행사 기획 시: POST /api/events
 *   - 행사 정보 수정 (상태 변경 포함): PUT /api/events/{id}
 *   - 행사 상세 조회 시 사진·지출·설문·일정 조율표가 함께 반환됨.
 */
@Entity
@Table(name = "cowork_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CoworkEvent extends BaseEntity {

    /** PK: auto increment */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 코호트 ID */
    @Column(name = "cohort_id", nullable = false)
    private Long cohortId;

    /** 행사명 (예: "2025 봄 축제") */
    @Column(nullable = false, length = 200)
    private String name;

    /** 행사 분류 (예: "축제", "학술", "친목") */
    @Column(length = 100)
    private String category;

    /**
     * 행사 진행 상태
     * 기본값: PLANNING
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EventStatus status = EventStatus.PLANNING;

    /** 행사 상세 설명 */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 행사 시작 날짜 */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** 행사 종료 날짜 */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** 행사 장소 (예: "본관 대강당") */
    @Column(length = 200)
    private String location;

    /** 주관 부서 (예: PLANNING, MANAGEMENT 등, 선택) */
    @Enumerated(EnumType.STRING)
    @Column(name = "lead_department", length = 20)
    private Department leadDepartment;

    /**
     * 담당자(주최자) 목록 (JSON 배열)
     * - 이름 문자열 리스트. 예: ["홍길동", "이철수"]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    private List<String> organizers;

    /** 행사 예산 (원화, 선택) */
    @Column
    private Long budget;

    /** 캘린더 표시용 커버 색상 (예: "#FF5733", 선택) */
    @Column(name = "cover_color", length = 20)
    private String coverColor;

    /** 행사 생성자 사용자 ID */
    @Column(name = "created_by")
    private Long createdBy;

    /**
     * 행사 정보 수정
     *
     * 동작: 모든 필드를 새 값으로 교체.
     * 사용 시점: EventService.updateEvent() 에서 호출 (PUT /api/events/{id}).
     */
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
