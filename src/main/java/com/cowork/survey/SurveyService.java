package com.cowork.survey;

import com.cowork.common.BusinessException;
import com.cowork.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SurveyService {

    private final SurveyRepository surveyRepository;
    private final SurveyQuestionRepository surveyQuestionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final SurveySubmissionRepository surveySubmissionRepository;
    private final ResponseAnswerRepository responseAnswerRepository;

    public List<Survey> getSurveys(Long cohortId, SurveyStatus status) {
        return surveyRepository.findFiltered(cohortId, status);
    }

    public long getResponseCount(Long surveyId) {
        return surveySubmissionRepository.countBySurveyId(surveyId);
    }

    public long getQuestionCount(Long surveyId) {
        return surveyQuestionRepository.countBySurveyId(surveyId);
    }

    public SurveyDetail getSurveyDetail(Long surveyId) {
        Survey survey = findSurvey(surveyId);
        return buildDetail(survey);
    }

    @Transactional
    public SurveyDetail createSurvey(Long cohortId, String title, String description, SurveyStatus status,
                                     Long createdBy, Long eventId, List<QuestionPayload> questions) {
        Survey survey = Survey.builder()
                .cohortId(cohortId)
                .title(title)
                .description(description)
                .status(status != null ? status : SurveyStatus.DRAFT)
                .createdBy(createdBy)
                .eventId(eventId)
                .build();
        surveyRepository.save(survey);
        replaceQuestions(survey.getId(), questions);
        return buildDetail(survey);
    }

    @Transactional
    public SurveyDetail updateSurvey(Long surveyId, String title, String description, SurveyStatus status,
                                     Long eventId, List<QuestionPayload> questions) {
        Survey survey = findSurvey(surveyId);
        survey.update(title, description, eventId);
        if (status != null) {
            survey.updateStatus(status);
        }
        replaceQuestions(surveyId, questions);
        return buildDetail(survey);
    }

    @Transactional
    public Survey updateStatus(Long surveyId, SurveyStatus status) {
        Survey survey = findSurvey(surveyId);
        survey.updateStatus(status);
        return survey;
    }

    @Transactional
    public void deleteSurvey(Long surveyId) {
        findSurvey(surveyId).softDelete();
    }

    @Transactional
    public SurveySubmission respond(Long surveyId, String respondentName, List<AnswerPayload> answers) {
        Survey survey = findSurvey(surveyId);
        if (survey.getStatus() == SurveyStatus.CLOSED) {
            throw new BusinessException(ErrorCode.SURVEY_CLOSED);
        }
        if (survey.getStatus() != SurveyStatus.OPEN) {
            throw new BusinessException(ErrorCode.SURVEY_NOT_OPEN);
        }

        List<SurveyQuestion> questions = surveyQuestionRepository.findBySurveyIdOrderByOrderIndexAsc(surveyId);
        Map<Long, SurveyQuestion> questionsById = questions.stream()
                .collect(Collectors.toMap(SurveyQuestion::getId, Function.identity()));

        Map<Long, AnswerPayload> answersByQuestion = answers == null
                ? Collections.emptyMap()
                : answers.stream().collect(Collectors.toMap(AnswerPayload::questionId, Function.identity(), (a, b) -> b));

        for (SurveyQuestion question : questions) {
            AnswerPayload payload = answersByQuestion.get(question.getId());
            boolean hasText = payload != null && StringUtils.hasText(payload.answerText());
            boolean hasSelection = payload != null && payload.selectedOptionIds() != null && !payload.selectedOptionIds().isEmpty();
            if (question.isRequired() && !hasText && !hasSelection) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
        }

        SurveySubmission submission = SurveySubmission.builder()
                .surveyId(surveyId)
                .respondentName(respondentName)
                .build();
        surveySubmissionRepository.save(submission);

        List<ResponseAnswer> savedAnswers = new ArrayList<>();
        for (AnswerPayload payload : answers == null ? List.<AnswerPayload>of() : answers) {
            if (!questionsById.containsKey(payload.questionId())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
            ResponseAnswer answer = ResponseAnswer.builder()
                    .responseId(submission.getId())
                    .questionId(payload.questionId())
                    .answerText(payload.answerText())
                    .selectedOptionIds(payload.selectedOptionIds())
                    .build();
            savedAnswers.add(answer);
        }
        responseAnswerRepository.saveAll(savedAnswers);
        return submission;
    }

    public SurveyResult getResults(Long surveyId) {
        SurveyDetail detail = getSurveyDetail(surveyId);
        List<QuestionResult> questionResults = new ArrayList<>();

        for (SurveyQuestion question : detail.questions()) {
            List<QuestionOption> options = detail.optionsByQuestion().getOrDefault(question.getId(), List.of());
            Map<Long, Long> optionCounts = new LinkedHashMap<>();
            for (QuestionOption option : options) {
                optionCounts.put(option.getId(), 0L);
            }
            List<String> textAnswers = new ArrayList<>();

            for (SurveySubmission submission : detail.submissions()) {
                List<ResponseAnswer> answers = detail.answersBySubmission().getOrDefault(submission.getId(), List.of());
                for (ResponseAnswer answer : answers) {
                    if (!question.getId().equals(answer.getQuestionId())) {
                        continue;
                    }

                    if (question.getType() == QuestionType.SHORT_TEXT || question.getType() == QuestionType.LONG_TEXT) {
                        if (StringUtils.hasText(answer.getAnswerText())) {
                            textAnswers.add(answer.getAnswerText());
                        }
                        continue;
                    }

                    if (answer.getSelectedOptionIds() != null && !answer.getSelectedOptionIds().isEmpty()) {
                        for (Long optionId : answer.getSelectedOptionIds()) {
                            optionCounts.computeIfPresent(optionId, (key, value) -> value + 1);
                        }
                    } else if (StringUtils.hasText(answer.getAnswerText())) {
                        options.stream()
                                .filter(option -> option.getLabel().equals(answer.getAnswerText()))
                                .findFirst()
                                .ifPresent(option -> optionCounts.computeIfPresent(option.getId(), (key, value) -> value + 1));
                    }
                }
            }

            questionResults.add(new QuestionResult(question, options, optionCounts, textAnswers));
        }

        return new SurveyResult(detail, questionResults);
    }

    private Survey findSurvey(Long surveyId) {
        return surveyRepository.findById(surveyId)
                .filter(survey -> !survey.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.SURVEY_NOT_FOUND));
    }

    private SurveyDetail buildDetail(Survey survey) {
        List<SurveyQuestion> questions = surveyQuestionRepository.findBySurveyIdOrderByOrderIndexAsc(survey.getId());
        List<Long> questionIds = questions.stream().map(SurveyQuestion::getId).toList();
        Map<Long, List<QuestionOption>> optionsByQuestion = questionIds.isEmpty()
                ? Map.of()
                : questionOptionRepository.findByQuestionIdInOrderByOrderIndexAsc(questionIds).stream()
                .collect(Collectors.groupingBy(QuestionOption::getQuestionId, LinkedHashMap::new, Collectors.toList()));

        List<SurveySubmission> submissions = surveySubmissionRepository.findBySurveyIdOrderBySubmittedAtDesc(survey.getId());
        List<Long> responseIds = submissions.stream().map(SurveySubmission::getId).toList();
        Map<Long, List<ResponseAnswer>> answersBySubmission = responseIds.isEmpty()
                ? Map.of()
                : responseAnswerRepository.findByResponseIdIn(responseIds).stream()
                .collect(Collectors.groupingBy(ResponseAnswer::getResponseId, LinkedHashMap::new, Collectors.toList()));

        return new SurveyDetail(survey, questions, optionsByQuestion, submissions, answersBySubmission);
    }

    private void replaceQuestions(Long surveyId, List<QuestionPayload> questions) {
        List<SurveyQuestion> existing = surveyQuestionRepository.findBySurveyIdOrderByOrderIndexAsc(surveyId);
        List<Long> existingQuestionIds = existing.stream().map(SurveyQuestion::getId).toList();
        if (!existingQuestionIds.isEmpty()) {
            questionOptionRepository.deleteByQuestionIdIn(existingQuestionIds);
        }
        if (!existing.isEmpty()) {
            surveyQuestionRepository.deleteBySurveyId(surveyId);
        }

        if (questions == null || questions.isEmpty()) {
            return;
        }

        List<SurveyQuestion> savedQuestions = new ArrayList<>();
        for (QuestionPayload payload : questions) {
            SurveyQuestion question = SurveyQuestion.builder()
                    .surveyId(surveyId)
                    .orderIndex(payload.orderIndex())
                    .title(payload.title())
                    .type(payload.type())
                    .required(payload.required())
                    .build();
            savedQuestions.add(surveyQuestionRepository.save(question));
        }

        List<QuestionOption> options = new ArrayList<>();
        for (int i = 0; i < savedQuestions.size(); i++) {
            SurveyQuestion savedQuestion = savedQuestions.get(i);
            QuestionPayload payload = questions.get(i);
            if (payload.options() == null) {
                continue;
            }
            for (int order = 0; order < payload.options().size(); order++) {
                String label = payload.options().get(order);
                if (!StringUtils.hasText(label)) {
                    continue;
                }
                options.add(QuestionOption.builder()
                        .questionId(savedQuestion.getId())
                        .orderIndex(order)
                        .label(label)
                        .build());
            }
        }
        if (!options.isEmpty()) {
            questionOptionRepository.saveAll(options);
        }
    }

    public record QuestionPayload(Integer orderIndex, String title, QuestionType type, boolean required,
                                  List<String> options) {
    }

    public record AnswerPayload(Long questionId, String answerText, List<Long> selectedOptionIds) {
    }

    public record SurveyDetail(Survey survey, List<SurveyQuestion> questions,
                               Map<Long, List<QuestionOption>> optionsByQuestion,
                               List<SurveySubmission> submissions,
                               Map<Long, List<ResponseAnswer>> answersBySubmission) {
    }

    public record QuestionResult(SurveyQuestion question, List<QuestionOption> options,
                                 Map<Long, Long> optionCounts, List<String> textAnswers) {
    }

    public record SurveyResult(SurveyDetail detail, List<QuestionResult> questionResults) {
    }
}
