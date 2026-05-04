package com.cowork.survey;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

/**
 * 응답 답변(ResponseAnswer) 엔티티
 *
 * 역할:
 *   응답자 제출(SurveySubmission) 내에서 각 질문에 대한 개별 답변을 저장한다.
 *   텍스트 답변(answerText)과 선택지 답변(selectedOptionIds) 중 하나 또는 둘 다를 가질 수 있다.
 *
 * 관계:
 *   - ResponseAnswer N : 1 SurveySubmission  (response_id FK → survey_responses.id)
 *   - ResponseAnswer N : 1 SurveyQuestion    (question_id FK)
 *
 * 답변 저장 방식:
 *   - 단답형/장문형 : answerText 에 텍스트, selectedOptionIds = null
 *   - 단일 선택    : answerText = null, selectedOptionIds = [optionId]
 *   - 복수 선택    : answerText = null, selectedOptionIds = [id1, id2, ...]
 *
 * 사용 시점:
 *   - 응답 제출(POST /api/surveys/{id}/respond) 시 SurveyService 에서 일괄 저장.
 *   - 결과 집계(GET /api/surveys/{id}/results) 시 질문별 답변 데이터를 읽어 통계 생성.
 */
@Entity
@Table(name = "response_answers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ResponseAnswer {

    /** PK: auto increment */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 응답(제출) ID (survey_responses.id 참조) */
    @Column(name = "response_id", nullable = false)
    private Long responseId;

    /** 답변 대상 질문 ID */
    @Column(name = "question_id", nullable = false)
    private Long questionId;

    /**
     * 텍스트 답변 (단답형·장문형 질문용)
     * - 선택형 질문이면 null.
     */
    @Column(name = "answer_text", columnDefinition = "TEXT")
    private String answerText;

    /**
     * 선택된 옵션 ID 목록 (JSON 배열)
     * - 텍스트 질문이면 null.
     * - 단일 선택이면 크기 1, 복수 선택이면 크기 N.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_option_ids", columnDefinition = "JSON")
    private List<Long> selectedOptionIds;
}
