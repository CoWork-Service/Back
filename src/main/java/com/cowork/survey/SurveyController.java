package com.cowork.survey;

import com.cowork.common.ApiResponse;
import com.cowork.user.User;
import com.cowork.user.UserRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 설문 관리 컨트롤러 (SurveyController)
 *
 * 역할:
 *   코호트 내 설문지 작성·배포·응답 수집·결과 집계 API 를 제공한다.
 *   기본 경로: /api/surveys
 *
 * 전체 흐름:
 *   1. 관리자가 설문 생성 (DRAFT 상태, 질문 목록 포함)
 *   2. PATCH /{id}/status 로 OPEN 상태로 전환 → 응답 수집 시작
 *   3. 응답자가 POST /{id}/respond 로 답변 제출
 *   4. GET /{id}/results 로 집계 결과 조회
 *   5. PATCH /{id}/status 로 CLOSED 상태로 전환 → 응답 마감
 *
 * 인증 필요: 대부분의 엔드포인트에 JWT 필요
 *            (/respond 는 링크 공유 방식으로 인증 없이 접근 가능하도록 설정 가능)
 */
@RestController
@RequestMapping("/api/surveys")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService surveyService;
    private final UserRepository userRepository;

    /**
     * 설문 목록 조회
     *
     * 동작: cohortId 기준으로 설문 목록을 조회하며, 상태 필터링 가능.
     *       각 항목에 질문 수와 응답 수가 포함된다.
     * 사용 시점: 설문 목록 화면에서 전체 설문을 표시할 때.
     *
     * @param cohortId 필수. 조회할 코호트 ID
     * @param status   선택. 상태 필터 (DRAFT / OPEN / CLOSED)
     * @return 설문 목록 (질문 수·응답 수 포함)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<SurveySummaryResponse>>> getSurveys(
            @RequestParam Long cohortId,
            @RequestParam(required = false) SurveyStatus status) {
        List<SurveySummaryResponse> surveys = surveyService.getSurveys(cohortId, status).stream()
                .map(survey -> SurveySummaryResponse.of(
                        survey,
                        surveyService.getQuestionCount(survey.getId()),
                        surveyService.getResponseCount(survey.getId())))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(surveys));
    }

    /**
     * 설문 생성
     *
     * 동작: 설문과 질문 목록(선택지 포함)을 함께 저장한다.
     *       생성자(createdBy)는 JWT 토큰에서 자동으로 설정된다.
     * 사용 시점: 새 설문지를 작성할 때.
     *
     * @param request     { cohortId, title, description, status, eventId, questions[] }
     *                    questions[] = [{ orderIndex, title, type, required, options[] }]
     * @param userDetails 현재 로그인 사용자 (생성자)
     * @return 생성된 설문 상세 정보 (질문 + 선택지 포함)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<SurveyDetailResponse>> createSurvey(
            @RequestBody SurveyRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        SurveyService.SurveyDetail detail = surveyService.createSurvey(
                request.getCohortId(),
                request.getTitle(),
                request.getDescription(),
                request.getStatus(),
                user.getId(),
                request.getEventId(),
                toQuestionPayloads(request.getQuestions())
        );
        return ResponseEntity.ok(ApiResponse.ok(SurveyDetailResponse.of(detail)));
    }

    /**
     * 설문 상세 조회
     *
     * 동작: 설문 기본 정보 + 질문 목록(선택지 포함) + 전체 응답 목록을 반환.
     * 사용 시점: 설문 상세 화면 또는 응답 페이지 진입 시.
     *
     * @param id 설문 ID
     * @return 설문 + 질문 목록 + 응답 목록
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SurveyDetailResponse>> getSurvey(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(SurveyDetailResponse.of(surveyService.getSurveyDetail(id))));
    }

    /**
     * 설문 수정
     *
     * 동작: 설문 기본 정보와 질문 목록을 수정한다.
     *       기존 질문·선택지는 삭제 후 재삽입 방식으로 교체된다.
     * 사용 시점: DRAFT 상태에서 설문 내용을 편집할 때.
     *            OPEN 상태에서 수정하면 이미 제출된 응답과 불일치 발생 위험 있음.
     *
     * @param id      설문 ID
     * @param request 수정할 설문 정보
     * @return 수정된 설문 상세 정보
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SurveyDetailResponse>> updateSurvey(
            @PathVariable Long id,
            @RequestBody SurveyRequest request) {
        SurveyService.SurveyDetail detail = surveyService.updateSurvey(
                id,
                request.getTitle(),
                request.getDescription(),
                request.getStatus(),
                request.getEventId(),
                toQuestionPayloads(request.getQuestions())
        );
        return ResponseEntity.ok(ApiResponse.ok(SurveyDetailResponse.of(detail)));
    }

    /**
     * 설문 상태 변경
     *
     * 동작: 설문의 status 를 변경한다.
     *       DRAFT → OPEN : 응답 수집 시작
     *       OPEN → CLOSED: 응답 마감
     * 사용 시점: 설문 배포 시작 또는 마감 시.
     *
     * @param id   설문 ID
     * @param body { "status": "OPEN" }
     * @return 업데이트된 설문 요약 (질문 수·응답 수 포함)
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<SurveySummaryResponse>> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Survey survey = surveyService.updateStatus(id, SurveyStatus.valueOf(body.get("status")));
        return ResponseEntity.ok(ApiResponse.ok(
                SurveySummaryResponse.of(survey, surveyService.getQuestionCount(id), surveyService.getResponseCount(id))
        ));
    }

    /**
     * 설문 삭제
     *
     * 동작: 설문과 연관된 질문·선택지·응답 데이터를 모두 삭제한다.
     * 사용 시점: 잘못 만든 설문 또는 더 이상 필요없는 설문을 제거할 때.
     *
     * @param id 설문 ID
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSurvey(@PathVariable Long id) {
        surveyService.deleteSurvey(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * 설문 응답 제출
     *
     * 동작:
     *   1. SurveySubmission 생성 (응답자 이름 + 제출 일시).
     *   2. 각 질문에 대한 ResponseAnswer 목록을 일괄 저장.
     *
     * 사용 시점: 응답자가 설문 페이지에서 모든 질문에 답변하고 제출할 때.
     *
     * @param id      설문 ID
     * @param request { respondentName, answers: [{ questionId, answerText, selectedOptionIds }] }
     * @return 제출된 응답 정보 (id, respondentName, submittedAt)
     */
    @PostMapping("/{id}/respond")
    public ResponseEntity<ApiResponse<ResponseSubmittedResponse>> respond(
            @PathVariable Long id,
            @RequestBody SurveyRespondRequest request) {
        SurveySubmission submission = surveyService.respond(
                id,
                request.getRespondentName(),
                request.getAnswers() == null ? List.of() : request.getAnswers().stream()
                        .map(answer -> new SurveyService.AnswerPayload(
                                answer.getQuestionId(),
                                answer.getAnswerText(),
                                answer.getSelectedOptionIds()
                        ))
                        .collect(Collectors.toList())
        );
        return ResponseEntity.ok(ApiResponse.ok(ResponseSubmittedResponse.of(submission)));
    }

    /**
     * 설문 결과 조회
     *
     * 동작: 전체 응답을 집계하여 질문별 결과를 반환한다.
     *       - 텍스트 질문 : 전체 텍스트 답변 목록
     *       - 선택 질문   : 선택지별 선택 횟수
     * 사용 시점: 설문 마감 후 결과 분석 화면에서 통계를 볼 때.
     *
     * @param id 설문 ID
     * @return { surveyId, title, responseCount, questions[{ questionId, title, type, optionCounts[], textAnswers[] }] }
     */
    @GetMapping("/{id}/results")
    public ResponseEntity<ApiResponse<SurveyResultResponse>> getResults(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(SurveyResultResponse.of(surveyService.getResults(id))));
    }

    /** JWT 의 username(= userId) 로 User 엔티티를 로드하는 내부 헬퍼 */
    private User getUser(UserDetails userDetails) {
        return userRepository.findById(Long.parseLong(userDetails.getUsername())).orElseThrow();
    }

    /** QuestionRequest 목록을 서비스 레이어의 QuestionPayload 로 변환 */
    private List<SurveyService.QuestionPayload> toQuestionPayloads(List<QuestionRequest> questions) {
        if (questions == null) {
            return List.of();
        }
        return questions.stream()
                .map(question -> new SurveyService.QuestionPayload(
                        question.getOrderIndex(),
                        question.getTitle(),
                        question.getType(),
                        question.isRequired(),
                        question.getOptions()
                ))
                .collect(Collectors.toList());
    }

    // ─── 요청/응답 DTOs ───────────────────────────────────────────────────────

    /** POST/PUT 설문 요청 바디 */
    @Getter
    static class SurveyRequest {
        private Long cohortId;
        private String title;
        private String description;
        private SurveyStatus status;
        private Long eventId;
        private List<QuestionRequest> questions;
    }

    /** 질문 요청 DTO */
    @Getter
    static class QuestionRequest {
        private Integer orderIndex;
        private String title;
        private QuestionType type;
        private boolean required;
        /** 선택형 질문의 선택지 레이블 목록 (예: ["매우만족", "만족", "보통"]) */
        private List<String> options;
    }

    /** POST /{id}/respond 요청 바디 */
    @Getter
    static class SurveyRespondRequest {
        private String respondentName;
        private List<AnswerRequest> answers;
    }

    /** 개별 답변 요청 DTO */
    @Getter
    static class AnswerRequest {
        private Long questionId;
        private String answerText;
        private List<Long> selectedOptionIds;
    }

    /** 설문 요약 응답 DTO (목록용) */
    record SurveySummaryResponse(Long id, Long cohortId, String title, String description,
                                 String status, Long createdBy, Long eventId, int questionCount,
                                 long responseCount, LocalDateTime createdAt, LocalDateTime updatedAt) {
        static SurveySummaryResponse of(Survey survey, long questionCount, long responseCount) {
            return new SurveySummaryResponse(
                    survey.getId(),
                    survey.getCohortId(),
                    survey.getTitle(),
                    survey.getDescription(),
                    survey.getStatus().name(),
                    survey.getCreatedBy(),
                    survey.getEventId(),
                    (int) questionCount,
                    responseCount,
                    survey.getCreatedAt(),
                    survey.getUpdatedAt()
            );
        }

        static SurveySummaryResponse of(SurveyService.SurveyDetail detail) {
            Survey survey = detail.survey();
            return new SurveySummaryResponse(
                    survey.getId(),
                    survey.getCohortId(),
                    survey.getTitle(),
                    survey.getDescription(),
                    survey.getStatus().name(),
                    survey.getCreatedBy(),
                    survey.getEventId(),
                    detail.questions().size(),
                    detail.submissions().size(),
                    survey.getCreatedAt(),
                    survey.getUpdatedAt()
            );
        }
    }

    /** 설문 상세 응답 DTO (질문 목록 + 응답 목록 포함) */
    record SurveyDetailResponse(SurveySummaryResponse survey, List<QuestionResponse> questions,
                                List<SubmissionResponse> responses) {
        static SurveyDetailResponse of(SurveyService.SurveyDetail detail) {
            Map<Long, List<QuestionOption>> optionsByQuestion = detail.optionsByQuestion();
            Map<Long, List<ResponseAnswer>> answersBySubmission = detail.answersBySubmission();
            return new SurveyDetailResponse(
                    SurveySummaryResponse.of(detail),
                    detail.questions().stream()
                            .map(question -> QuestionResponse.of(question, optionsByQuestion.getOrDefault(question.getId(), List.of())))
                            .collect(Collectors.toList()),
                    detail.submissions().stream()
                            .map(submission -> SubmissionResponse.of(submission, answersBySubmission.getOrDefault(submission.getId(), List.of())))
                            .collect(Collectors.toList())
            );
        }
    }

    /** 질문 응답 DTO (선택지 포함) */
    record QuestionResponse(Long id, Integer orderIndex, String title, String type,
                            boolean required, List<OptionResponse> options) {
        static QuestionResponse of(SurveyQuestion question, List<QuestionOption> options) {
            return new QuestionResponse(
                    question.getId(),
                    question.getOrderIndex(),
                    question.getTitle(),
                    question.getType().name(),
                    question.isRequired(),
                    options.stream().map(OptionResponse::of).collect(Collectors.toList())
            );
        }
    }

    /** 선택지 응답 DTO */
    record OptionResponse(Long id, Integer orderIndex, String label) {
        static OptionResponse of(QuestionOption option) {
            return new OptionResponse(option.getId(), option.getOrderIndex(), option.getLabel());
        }
    }

    /** 응답 제출 내역 DTO (응답자 + 답변 목록) */
    record SubmissionResponse(Long id, String respondentName, LocalDateTime submittedAt,
                              List<SubmissionAnswerResponse> answers) {
        static SubmissionResponse of(SurveySubmission submission, List<ResponseAnswer> answers) {
            return new SubmissionResponse(
                    submission.getId(),
                    submission.getRespondentName(),
                    submission.getSubmittedAt(),
                    answers.stream().map(SubmissionAnswerResponse::of).collect(Collectors.toList())
            );
        }
    }

    /** 개별 답변 응답 DTO */
    record SubmissionAnswerResponse(Long questionId, String answerText, List<Long> selectedOptionIds) {
        static SubmissionAnswerResponse of(ResponseAnswer answer) {
            return new SubmissionAnswerResponse(answer.getQuestionId(), answer.getAnswerText(), answer.getSelectedOptionIds());
        }
    }

    /** POST /{id}/respond 응답 DTO (제출 완료 확인용) */
    record ResponseSubmittedResponse(Long id, String respondentName, LocalDateTime submittedAt) {
        static ResponseSubmittedResponse of(SurveySubmission submission) {
            return new ResponseSubmittedResponse(submission.getId(), submission.getRespondentName(), submission.getSubmittedAt());
        }
    }

    /** 설문 결과 응답 DTO */
    record SurveyResultResponse(Long surveyId, String title, long responseCount,
                                List<QuestionResultResponse> questions) {
        static SurveyResultResponse of(SurveyService.SurveyResult result) {
            return new SurveyResultResponse(
                    result.detail().survey().getId(),
                    result.detail().survey().getTitle(),
                    result.detail().submissions().size(),
                    result.questionResults().stream().map(QuestionResultResponse::of).collect(Collectors.toList())
            );
        }
    }

    /** 질문별 결과 응답 DTO (선택지 카운트 + 텍스트 답변 목록) */
    record QuestionResultResponse(Long questionId, String title, String type,
                                  List<OptionCountResponse> optionCounts, List<String> textAnswers) {
        static QuestionResultResponse of(SurveyService.QuestionResult result) {
            List<OptionCountResponse> optionCounts = result.options().stream()
                    .map(option -> new OptionCountResponse(option.getId(), option.getLabel(),
                            result.optionCounts().getOrDefault(option.getId(), 0L)))
                    .collect(Collectors.toList());
            return new QuestionResultResponse(
                    result.question().getId(),
                    result.question().getTitle(),
                    result.question().getType().name(),
                    optionCounts,
                    result.textAnswers()
            );
        }
    }

    /** 선택지별 선택 횟수 응답 DTO */
    record OptionCountResponse(Long optionId, String label, Long count) {
    }
}
