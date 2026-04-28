package com.cowork.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TimetableSubmissionRepository extends JpaRepository<TimetableSubmission, Long> {

    List<TimetableSubmission> findByTimetableIdOrderBySubmittedAtDesc(Long timetableId);

    Optional<TimetableSubmission> findByTimetableIdAndParticipantId(Long timetableId, Long participantId);

    void deleteByParticipantIdIn(Collection<Long> participantIds);
}
