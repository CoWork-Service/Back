package com.cowork.survey;

import com.cowork.common.ApiResponse;
import com.cowork.user.User;
import com.cowork.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Survey", description = "설문 관리 API — 설문 CRUD, 응답 수집, 결과 집계")
@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequestMapping("/api/surveys")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService surveyService;
    private final UserRepository userRepository;

    @Operation(
            summary = "설문 목록 조회",
            description = """
                    코호트의 설문 목록을 조회합니다.

                    **사용 시점:** 설문 목록 화면에서 전체 설문을 표시할 때.

                    각 항목에 **질문 수**와 **응답 수**가 포함됩니다.

                    **상태(status) 값:**
                    - `DRAFT` — 초안 (아직 공개하지 않음)
                    - `OPEN` — 응답 수집 중
                    - `CLOSED` — 마감
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "설문 목록 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": [
                                        {
                                          "id": 1,
                                          "cohortId": 5,
                                          "title": "MT 만족도 조사",
                                          "description": "이번 MT는 어땠나요?",
                                          "status": "OPEN",
                                          "createdBy": 1,
                                          "eventId": 1,
                                          "questionCount": 5,
                                          "responseCount": 12,
                                          "createdAt": "2025-03-16T10:00:00",
                                          "updatedAt": "2025-03-16T10:00:00"
                                        }
                                      ],
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<SurveySummaryResponse>>> getSurveys(
            @Parameter(description = "코호트 ID (필수)", required = true, example = "5") @RequestParam Long cohortId,
            @Parameter(description = "상태 필터 (DRAFT / OPEN / CLOSED)", example = "OPEN") @RequestParam(required = false) SurveyStatus status) {
        List<SurveySummaryResponse> surveys = surveyService.getSurveys(cohortId, status).stream()
                .map(survey -> SurveySummaryResponse.of(
                        survey,
                        surveyService.getQuestionCount(survey.getId()),
                        surveyService.getResponseCount(survey.getId())))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(surveys));
    }

    @Operation(
            summary = "설문 생성",
            description = """
                    설문과 질문 목록(선택지 포함)을 함께 생성합니다.

                    **사용 시점:** 새 설문지를 작성할 때.

                    **질문 유형(type) 값:**
                    - `SHORT_TEXT` — 단답형 텍스트
                    - `LONG_TEXT` — 장문형 텍스트
                    - `SINGLE` — 단일 선택 (라디오)
                    - `MULTIPLE` — 복수 선택 (체크박스)

                    **초기 상태:** `DRAFT`로 생성 후 `PATCH /{id}/status`로 `OPEN` 전환을 권장합니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "설문 생성 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "survey": {
                                          "id": 3, "title": "종강 파티 메뉴 투표", "status": "DRAFT",
                                          "questionCount": 2, "responseCount": 0
                                        },
                                        "questions": [
                                          {
                                            "id": 5, "orderIndex": 1, "title": "선호하는 음식 종류는?",
                                            "type": "SINGLE", "required": true,
                                            "options": [
                                              { "id": 1, "orderIndex": 1, "label": "한식" },
                                              { "id": 2, "orderIndex": 2, "label": "중식" },
                                              { "id": 3, "orderIndex": 3, "label": "양식" }
                                            ]
                                          },
                                          {
                                            "id": 6, "orderIndex": 2, "title": "기타 의견이 있으신가요?",
                                            "type": "LONG_TEXT", "required": false, "options": []
                                          }
                                        ],
                                        "responses": []
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
    @PostMapping
    public ResponseEntity<ApiResponse<SurveyDetailResponse>> createSurvey(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "설문 생성 요청",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "cohortId": 5,
                              "title": "종강 파티 메뉴 투표",
                              "description": "종강 파티에서 먹고 싶은 음식을 선택해주세요",
                              "status": "DRAFT",
                              "eventId": 2,
                              "questions": [
                                {
                                  "orderIndex": 1,
                                  "title": "선호하는 음식 종류는?",
                                  "type": "SINGLE",
                                  "required": true,
                                  "options": ["한식", "중식", "양식"]
                                },
                                {
                                  "orderIndex": 2,
                                  "title": "기타 의견이 있으신가요?",
                                  "type": "LONG_TEXT",
                                  "required": false,
                                  "options": []
                                }
                              ]
                            }
                            """)))
            @RequestBody SurveyRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUser(userDetails);
        SurveyService.SurveyDetail detail = surveyService.createSurvey(
                request.getCohortId(), request.getTitle(), request.getDescription(),
                request.getStatus(), user.getId(), request.getEventId(),
                toQuestionPayloads(request.getQuestions())
        );
        return ResponseEntity.ok(ApiResponse.ok(SurveyDetailResponse.of(detail)));
    }

    @Operation(
            summary = "설문 상세 조회",
            description = """
                    설문 기본 정보 + 질문 목록(선택지 포함) + 전체 응답 목록을 반환합니다.

                    **사용 시점:** 설문 상세 화면 또는 응답 페이지 진입 시.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "설문 상세 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "survey": { "id": 1, "title": "MT 만족도 조사", "status": "OPEN", "questionCount": 5, "responseCount": 12 },
                                        "questions": [
                                          {
                                            "id": 1, "orderIndex": 1, "title": "MT 전반적인 만족도는?",
                                            "type": "SINGLE", "required": true,
                                            "options": [
                                              { "id": 1, "orderIndex": 1, "label": "매우 만족" },
                                              { "id": 2, "orderIndex": 2, "label": "만족" },
                                              { "id": 3, "orderIndex": 3, "label": "보통" },
                                              { "id": 4, "orderIndex": 4, "label": "불만족" }
                                            ]
                                          }
                                        ],
                                        "responses": [
                                          {
                                            "id": 1, "respondentName": "홍길동", "submittedAt": "2025-03-16T14:00:00",
                                            "answers": [{ "questionId": 1, "answerText": null, "selectedOptionIds": [1] }]
                                          }
                                        ]
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "설문을 찾을 수 없음")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SurveyDetailResponse>> getSurvey(
            @Parameter(description = "설문 ID", required = true, example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(SurveyDetailResponse.of(surveyService.getSurveyDetail(id))));
    }

    @Operation(
            summary = "설문 수정",
            description = """
                    설문 기본 정보와 질문 목록을 수정합니다.

                    **사용 시점:** DRAFT 상태에서 설문 내용을 편집할 때.

                    **주의:** OPEN 상태에서 수정하면 이미 제출된 응답과 불일치가 발생할 수 있습니다.
                    기존 질문·선택지는 삭제 후 재삽입 방식으로 교체됩니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "설문 수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "설문을 찾을 수 없음")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SurveyDetailResponse>> updateSurvey(
            @Parameter(description = "설문 ID", required = true, example = "1") @PathVariable Long id,
            @RequestBody SurveyRequest request) {
        SurveyService.SurveyDetail detail = surveyService.updateSurvey(
                id, request.getTitle(), request.getDescription(), request.getStatus(),
                request.getEventId(), toQuestionPayloads(request.getQuestions())
        );
        return ResponseEntity.ok(ApiResponse.ok(SurveyDetailResponse.of(detail)));
    }

    @Operation(
            summary = "설문 상태 변경",
            description = """
                    설문의 상태를 변경합니다.

                    **사용 시점:** 설문 배포 시작 또는 마감 시.

                    **상태 전환 흐름:**
                    ```
                    DRAFT → OPEN (배포 시작)
                    OPEN  → CLOSED (마감)
                    ```

                    **status 값:** `DRAFT` / `OPEN` / `CLOSED`
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "상태 변경 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "id": 3,
                                        "title": "종강 파티 메뉴 투표",
                                        "status": "OPEN",
                                        "questionCount": 2,
                                        "responseCount": 0
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "설문을 찾을 수 없음")
    })
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<SurveySummaryResponse>> updateStatus(
            @Parameter(description = "설문 ID", required = true, example = "3") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "상태 변경 요청",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            { "status": "OPEN" }
                            """)))
            @RequestBody Map<String, String> body) {
        Survey survey = surveyService.updateStatus(id, SurveyStatus.valueOf(body.get("status")));
        return ResponseEntity.ok(ApiResponse.ok(
                SurveySummaryResponse.of(survey, surveyService.getQuestionCount(id), surveyService.getResponseCount(id))
        ));
    }

    @Operation(
            summary = "설문 삭제",
            description = """
                    설문과 연관된 질문·선택지·응답 데이터를 모두 삭제합니다.

                    **사용 시점:** 잘못 만든 설문 또는 더 이상 필요없는 설문을 제거할 때.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "설문 삭제 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "success": true, "data": null, "message": null, "code": null }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "설문을 찾을 수 없음")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSurvey(
            @Parameter(description = "설문 ID", required = true, example = "1") @PathVariable Long id) {
        surveyService.deleteSurvey(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(
            summary = "설문 응답 제출 (인증 불필요)",
            description = """
                    응답자가 설문에 답변을 제출합니다.

                    **사용 시점:** 응답자가 설문 페이지에서 모든 질문에 답변하고 제출할 때.

                    **인증 불필요** — 공유 링크 방식으로 동작합니다.

                    **답변 형식:**
                    - 텍스트 질문 (SHORT_TEXT / LONG_TEXT): `answerText` 필드 사용, `selectedOptionIds`는 빈 배열
                    - 선택 질문 (SINGLE / MULTIPLE): `selectedOptionIds` 배열에 선택지 ID 입력, `answerText`는 null
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "응답 제출 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "id": 13,
                                        "respondentName": "박민준",
                                        "submittedAt": "2025-05-10T11:30:00"
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "설문이 OPEN 상태가 아님",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "message": "현재 응답을 받지 않는 설문입니다.",
                                      "code": "SURVEY_NOT_OPEN"
                                    }
                                    """)))
    })
    @PostMapping("/{id}/respond")
    public ResponseEntity<ApiResponse<ResponseSubmittedResponse>> respond(
            @Parameter(description = "설문 ID", required = true, example = "3") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "설문 응답 제출 요청",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "respondentName": "박민준",
                              "answers": [
                                {
                                  "questionId": 5,
                                  "answerText": null,
                                  "selectedOptionIds": [2]
                                },
                                {
                                  "questionId": 6,
                                  "answerText": "디저트도 있으면 좋겠어요!",
                                  "selectedOptionIds": []
                                }
                              ]
                            }
                            """)))
            @RequestBody SurveyRespondRequest request) {
        SurveySubmission submission = surveyService.respond(
                id, request.getRespondentName(),
                request.getAnswers() == null ? List.of() : request.getAnswers().stream()
                        .map(answer -> new SurveyService.AnswerPayload(
                                answer.getQuestionId(), answer.getAnswerText(), answer.getSelectedOptionIds()))
                        .collect(Collectors.toList())
        );
        return ResponseEntity.ok(ApiResponse.ok(ResponseSubmittedResponse.of(submission)));
    }

    @Operation(
            summary = "설문 결과 조회",
            description = """
                    전체 응답을 집계하여 질문별 결과를 반환합니다.

                    **사용 시점:** 설문 마감 후 결과 분석 화면에서 통계를 볼 때.

                    **결과 유형:**
                    - 텍스트 질문 → `textAnswers` 배열에 전체 텍스트 답변 목록
                    - 선택 질문 → `optionCounts` 배열에 선택지별 선택 횟수
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "설문 결과 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "surveyId": 3,
                                        "title": "종강 파티 메뉴 투표",
                                        "responseCount": 20,
                                        "questions": [
                                          {
                                            "questionId": 5,
                                            "title": "선호하는 음식 종류는?",
                                            "type": "SINGLE",
                                            "optionCounts": [
                                              { "optionId": 1, "label": "한식", "count": 8 },
                                              { "optionId": 2, "label": "중식", "count": 7 },
                                              { "optionId": 3, "label": "양식", "count": 5 }
                                            ],
                                            "textAnswers": []
                                          },
                                          {
                                            "questionId": 6,
                                            "title": "기타 의견이 있으신가요?",
                                            "type": "LONG_TEXT",
                                            "optionCounts": [],
                                            "textAnswers": ["디저트도 있으면 좋겠어요!", "장소 넓은 곳으로 해주세요"]
                                          }
                                        ]
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
    @GetMapping("/{id}/results")
    public ResponseEntity<ApiResponse<SurveyResultResponse>> getResults(
            @Parameter(description = "설문 ID", required = true, example = "3") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(SurveyResultResponse.of(surveyService.getResults(id))));
    }

    private User getUser(UserDetails userDetails) {
        return userRepository.findById(Long.parseLong(userDetails.getUsername())).orElseThrow();
    }

    private List<SurveyService.QuestionPayload> toQuestionPayloads(List<QuestionRequest> questions) {
        if (questions == null) return List.of();
        return questions.stream()
                .map(question -> new SurveyService.QuestionPayload(
                        question.getOrderIndex(), question.getTitle(), question.getType(),
                        question.isRequired(), question.getOptions()))
                .collect(Collectors.toList());
    }

    // ─── 요청/응답 DTOs ───────────────────────────────────────────────────────

    @Getter
    static class SurveyRequest {
        private Long cohortId;
        private String title;
        private String description;
        private SurveyStatus status;
        private Long eventId;
        private List<QuestionRequest> questions;
    }

    @Getter
    static class QuestionRequest {
        private Integer orderIndex;
        private String title;
        private QuestionType type;
        private boolean required;
        private List<String> options;
    }

    @Getter
    static class SurveyRespondRequest {
        private String respondentName;
        private List<AnswerRequest> answers;
    }

    @Getter
    static class AnswerRequest {
        private Long questionId;
        private String answerText;
        private List<Long> selectedOptionIds;
    }

    record SurveySummaryResponse(Long id, Long cohortId, String title, String description,
                                 String status, Long createdBy, Long eventId, int questionCount,
                                 long responseCount, LocalDateTime createdAt, LocalDateTime updatedAt) {
        static SurveySummaryResponse of(Survey survey, long questionCount, long responseCount) {
            return new SurveySummaryResponse(
                    survey.getId(), survey.getCohortId(), survey.getTitle(), survey.getDescription(),
                    survey.getStatus().name(), survey.getCreatedBy(), survey.getEventId(),
                    (int) questionCount, responseCount, survey.getCreatedAt(), survey.getUpdatedAt()
            );
        }

        static SurveySummaryResponse of(SurveyService.SurveyDetail detail) {
            Survey survey = detail.survey();
            return new SurveySummaryResponse(
                    survey.getId(), survey.getCohortId(), survey.getTitle(), survey.getDescription(),
                    survey.getStatus().name(), survey.getCreatedBy(), survey.getEventId(),
                    detail.questions().size(), detail.submissions().size(),
                    survey.getCreatedAt(), survey.getUpdatedAt()
            );
        }
    }

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

    record QuestionResponse(Long id, Integer orderIndex, String title, String type,
                            boolean required, List<OptionResponse> options) {
        static QuestionResponse of(SurveyQuestion question, List<QuestionOption> options) {
            return new QuestionResponse(
                    question.getId(), question.getOrderIndex(), question.getTitle(),
                    question.getType().name(), question.isRequired(),
                    options.stream().map(OptionResponse::of).collect(Collectors.toList())
            );
        }
    }

    record OptionResponse(Long id, Integer orderIndex, String label) {
        static OptionResponse of(QuestionOption option) {
            return new OptionResponse(option.getId(), option.getOrderIndex(), option.getLabel());
        }
    }

    record SubmissionResponse(Long id, String respondentName, LocalDateTime submittedAt,
                              List<SubmissionAnswerResponse> answers) {
        static SubmissionResponse of(SurveySubmission submission, List<ResponseAnswer> answers) {
            return new SubmissionResponse(
                    submission.getId(), submission.getRespondentName(), submission.getSubmittedAt(),
                    answers.stream().map(SubmissionAnswerResponse::of).collect(Collectors.toList())
            );
        }
    }

    record SubmissionAnswerResponse(Long questionId, String answerText, List<Long> selectedOptionIds) {
        static SubmissionAnswerResponse of(ResponseAnswer answer) {
            return new SubmissionAnswerResponse(answer.getQuestionId(), answer.getAnswerText(), answer.getSelectedOptionIds());
        }
    }

    record ResponseSubmittedResponse(Long id, String respondentName, LocalDateTime submittedAt) {
        static ResponseSubmittedResponse of(SurveySubmission submission) {
            return new ResponseSubmittedResponse(submission.getId(), submission.getRespondentName(), submission.getSubmittedAt());
        }
    }

    record SurveyResultResponse(Long surveyId, String title, long responseCount,
                                List<QuestionResultResponse> questions) {
        static SurveyResultResponse of(SurveyService.SurveyResult result) {
            return new SurveyResultResponse(
                    result.detail().survey().getId(), result.detail().survey().getTitle(),
                    result.detail().submissions().size(),
                    result.questionResults().stream().map(QuestionResultResponse::of).collect(Collectors.toList())
            );
        }
    }

    record QuestionResultResponse(Long questionId, String title, String type,
                                  List<OptionCountResponse> optionCounts, List<String> textAnswers) {
        static QuestionResultResponse of(SurveyService.QuestionResult result) {
            List<OptionCountResponse> optionCounts = result.options().stream()
                    .map(option -> new OptionCountResponse(option.getId(), option.getLabel(),
                            result.optionCounts().getOrDefault(option.getId(), 0L)))
                    .collect(Collectors.toList());
            return new QuestionResultResponse(
                    result.question().getId(), result.question().getTitle(),
                    result.question().getType().name(), optionCounts, result.textAnswers()
            );
        }
    }

    record OptionCountResponse(Long optionId, String label, Long count) {
    }
}
