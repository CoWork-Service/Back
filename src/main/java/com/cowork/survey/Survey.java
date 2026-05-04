package com.cowork.survey;

import com.cowork.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 설문(Survey) 엔티티
 *
 * 역할:
 *   코호트 내에서 사용하는 설문지를 관리한다.
 *   여러 개의 질문(SurveyQuestion)을 포함하며, 응답자들의 답변(SurveySubmission)을 수집한다.
 *   행사(event_id)와 연결하면 행사 상세 조회 시 관련 설문이 함께 표시된다.
 *
 * 관계:
 *   - Survey 1 : N SurveyQuestion    (survey_questions 테이블의 survey_id FK)
 *   - Survey 1 : N SurveySubmission  (survey_responses 테이블의 survey_id FK)
 *   - cohort_id, event_id 로 코호트·행사와 연결
 *
 * 상태 흐름 (SurveyStatus):
 *   DRAFT (작성 중) → OPEN (응답 수집 중) → CLOSED (마감)
 *
 * 사용 시점:
 *   - 설문 생성: POST /api/surveys (질문 목록 포함)
 *   - 상태 변경 (OPEN/CLOSED): PATCH /api/surveys/{id}/status
 *   - 응답 수집: POST /api/surveys/{id}/respond
 *   - 결과 집계: GET /api/surveys/{id}/results
 */
@Entity
@Table(name = "surveys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Survey extends BaseEntity {

    /** PK: auto increment */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 코호트 ID */
    @Column(name = "cohort_id", nullable = false)
    private Long cohortId;

    /** 설문 제목 (예: "3월 행사 만족도 조사") */
    @Column(nullable = false, length = 200)
    private String title;

    /** 설문 안내 설명 (응답자에게 표시) */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * 설문 상태
     * - DRAFT  : 작성 중, 응답 불가
     * - OPEN   : 응답 수집 중
     * - CLOSED : 마감, 더 이상 응답 불가
     * 기본값: DRAFT
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SurveyStatus status = SurveyStatus.DRAFT;

    /** 설문 생성자 사용자 ID */
    @Column(name = "created_by")
    private Long createdBy;

    /** 연결된 행사 ID (행사 관련 설문인 경우 설정, 선택) */
    @Column(name = "event_id")
    private Long eventId;

    /**
     * 설문 기본 정보 수정
     *
     * 동작: 제목·설명·행사ID 를 새 값으로 교체.
     * 사용 시점: SurveyService.updateSurvey() 에서 호출 (PUT /api/surveys/{id}).
     */
    public void update(String title, String description, Long eventId) {
        this.title = title;
        this.description = description;
        this.eventId = eventId;
    }

    /**
     * 설문 상태 변경
     *
     * 동작: status 를 새 상태로 변경.
     * 사용 시점: SurveyService.updateStatus() 에서 호출 (PATCH /api/surveys/{id}/status).
     *
     * @param status 새 상태 (DRAFT / OPEN / CLOSED)
     */
    public void updateStatus(SurveyStatus status) {
        this.status = status;
    }
}
