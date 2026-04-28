package com.cowork.survey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SurveyQuestionRepository extends JpaRepository<SurveyQuestion, Long> {

    List<SurveyQuestion> findBySurveyIdOrderByOrderIndexAsc(Long surveyId);

    long countBySurveyId(Long surveyId);

    void deleteBySurveyId(Long surveyId);
}
