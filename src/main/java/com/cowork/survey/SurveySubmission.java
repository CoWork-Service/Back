package com.cowork.survey;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 설문 응답 제출(SurveySubmission) 엔티티
 *
 * 역할:
 *   응답자 한 명이 설문(Survey)에 제출한 응답의 헤더 역할을 한다.
 *   응답자 이름과 제출 일시를 저장하며, 실제 각 질문의 답변은 ResponseAnswer 에 별도로 저장된다.
 *
 * 관계:
 *   - SurveySubmission N : 1 Survey       (survey_id FK)
 *   - SurveySubmission 1 : N ResponseAnswer (response_answers 테이블의 response_id FK)
 *
 * 사용 시점:
 *   - 응답자가 POST /api/surveys/{id}/respond 를 호출할 때 생성.
 *   - SurveyService.respond() 에서 먼저 SurveySubmission 을 저장한 후,
 *     answers 목록을 ResponseAnswer 로 변환해 일괄 저장.
 *   - 결과 집계 시 survey_id 로 조회하여 전체 응답 수를 파악.
 */
@Entity
@Table(name = "survey_responses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SurveySubmission {

    /** PK: auto increment */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 응답 대상 설문 ID */
    @Column(name = "survey_id", nullable = false)
    private Long surveyId;

    /**
     * 응답자 이름 (익명 허용 시 null 가능)
     * - 설문이 공개형이면 이름 없이 제출 가능.
     */
    @Column(name = "respondent_name", length = 50)
    private String respondentName;

    /** 응답 제출 일시 (변경 불가) */
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    /** JPA 최초 저장 직전 — submittedAt 초기화 */
    @PrePersist
    protected void onCreate() {
        this.submittedAt = LocalDateTime.now();
    }
}
