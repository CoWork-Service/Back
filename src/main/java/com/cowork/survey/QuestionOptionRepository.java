package com.cowork.survey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, Long> {

    List<QuestionOption> findByQuestionIdInOrderByOrderIndexAsc(Collection<Long> questionIds);

    void deleteByQuestionIdIn(Collection<Long> questionIds);
}
