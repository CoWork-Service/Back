package com.cowork.survey;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 설문 질문(SurveyQuestion) 엔티티
 *
 * 역할:
 *   설문(Survey) 에 포함된 개별 질문을 나타낸다.
 *   질문 유형(단답형, 객관식, 체크박스 등) 과 순서를 관리한다.
 *   객관식/체크박스 유형이면 QuestionOption 목록을 통해 선택지를 제공한다.
 *
 * 관계:
 *   - SurveyQuestion N : 1 Survey         (survey_id FK)
 *   - SurveyQuestion 1 : N QuestionOption (question_options 테이블의 question_id FK)
 *
 * 질문 유형 (QuestionType):
 *   - SHORT_TEXT   : 단답형 텍스트
 *   - LONG_TEXT    : 장문형 텍스트
 *   - SINGLE       : 단일 선택 (라디오)
 *   - MULTIPLE     : 복수 선택 (체크박스)
 *
 * 사용 시점:
 *   - 설문 생성/수정 시 questions 목록에 포함되어 함께 저장.
 *   - 설문 상세 조회 시 질문 순서(orderIndex) 에 따라 정렬되어 반환.
 */
@Entity
@Table(name = "survey_questions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SurveyQuestion {

    /** PK: auto increment */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 설문 ID */
    @Column(name = "survey_id", nullable = false)
    private Long surveyId;

    /** 질문 표시 순서 (1부터 시작, 오름차순으로 정렬) */
    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    /** 질문 텍스트 (예: "행사에 만족하셨나요?") */
    @Column(nullable = false, length = 500)
    private String title;

    /**
     * 질문 유형
     * - SHORT_TEXT / LONG_TEXT : 텍스트 입력
     * - SINGLE / MULTIPLE      : 선택지 중 선택
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuestionType type;

    /**
     * 필수 응답 여부
     * - true  : 빈 칸 제출 불가
     * - false : 선택 응답
     */
    @Column(nullable = false)
    private boolean required;

    /** 질문 생성 일시 (변경 불가) */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 질문 마지막 수정 일시 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** JPA 최초 저장 직전 — 날짜 필드 초기화 */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /** JPA 업데이트 직전 — updatedAt 갱신 */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
