package com.cowork.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TimetableParticipantRepository extends JpaRepository<TimetableParticipant, Long> {

    List<TimetableParticipant> findByTimetableIdOrderByCreatedAtAsc(Long timetableId);

    long countByTimetableId(Long timetableId);

    Optional<TimetableParticipant> findByTimetableIdAndName(Long timetableId, String name);

    void deleteByIdIn(Collection<Long> ids);
}
