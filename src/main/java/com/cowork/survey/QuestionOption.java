package com.cowork.survey;

import jakarta.persistence.*;
import lombok.*;

/**
 * 질문 선택지(QuestionOption) 엔티티
 *
 * 역할:
 *   객관식/체크박스 유형의 설문 질문(SurveyQuestion)에 대한 개별 선택지를 저장한다.
 *   응답자는 이 선택지들 중에서 하나 또는 여러 개를 선택한다.
 *
 * 관계:
 *   - QuestionOption N : 1 SurveyQuestion  (question_id FK)
 *
 * 사용 시점:
 *   - 설문 생성·수정 시 각 질문의 options 목록으로 함께 저장.
 *   - 응답 제출 시 ResponseAnswer.selectedOptionIds 에 선택된 옵션 ID 목록이 저장됨.
 *   - 결과 집계 시 옵션별 선택 횟수를 카운팅.
 */
@Entity
@Table(name = "question_options")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class QuestionOption {

    /** PK: auto increment */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 질문 ID */
    @Column(name = "question_id", nullable = false)
    private Long questionId;

    /** 선택지 표시 순서 (1부터 시작) */
    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    /** 선택지 텍스트 (예: "매우 만족", "보통", "불만족") */
    @Column(nullable = false, length = 500)
    private String label;
}
