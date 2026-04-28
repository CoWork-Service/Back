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

@RestController
@RequestMapping("/api/surveys")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService surveyService;
    private final UserRepository userRepository;

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

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SurveyDetailResponse>> getSurvey(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(SurveyDetailResponse.of(surveyService.getSurveyDetail(id))));
    }

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

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<SurveySummaryResponse>> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Survey survey = surveyService.updateStatus(id, SurveyStatus.valueOf(body.get("status")));
        return ResponseEntity.ok(ApiResponse.ok(
                SurveySummaryResponse.of(survey, surveyService.getQuestionCount(id), surveyService.getResponseCount(id))
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSurvey(@PathVariable Long id) {
        surveyService.deleteSurvey(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

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

    @GetMapping("/{id}/results")
    public ResponseEntity<ApiResponse<SurveyResultResponse>> getResults(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(SurveyResultResponse.of(surveyService.getResults(id))));
    }

    private User getUser(UserDetails userDetails) {
        return userRepository.findById(Long.parseLong(userDetails.getUsername())).orElseThrow();
    }

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
                    question.getId(),
                    question.getOrderIndex(),
                    question.getTitle(),
                    question.getType().name(),
                    question.isRequired(),
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
                    submission.getId(),
                    submission.getRespondentName(),
                    submission.getSubmittedAt(),
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
                    result.detail().survey().getId(),
                    result.detail().survey().getTitle(),
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
                    result.question().getId(),
                    result.question().getTitle(),
                    result.question().getType().name(),
                    optionCounts,
                    result.textAnswers()
            );
        }
    }

    record OptionCountResponse(Long optionId, String label, Long count) {
    }
}
