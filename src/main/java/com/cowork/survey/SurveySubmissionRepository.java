package com.cowork.survey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SurveySubmissionRepository extends JpaRepository<SurveySubmission, Long> {

    List<SurveySubmission> findBySurveyIdOrderBySubmittedAtDesc(Long surveyId);

    long countBySurveyId(Long surveyId);
}
