package com.cowork.survey;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SurveyRepository extends JpaRepository<Survey, Long> {

    @Query("SELECT s FROM Survey s WHERE s.cohortId = :cohortId AND s.deletedAt IS NULL " +
           "AND (:status IS NULL OR s.status = :status) ORDER BY s.createdAt DESC")
    List<Survey> findFiltered(Long cohortId, SurveyStatus status);

    List<Survey> findByEventIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long eventId);
}
