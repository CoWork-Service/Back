package com.cowork.schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TimetableRepository extends JpaRepository<Timetable, Long> {

    @Query("SELECT t FROM Timetable t WHERE t.cohortId = :cohortId AND t.deletedAt IS NULL " +
           "AND (:status IS NULL OR t.status = :status) ORDER BY t.createdAt DESC")
    List<Timetable> findFiltered(Long cohortId, TimetableStatus status);

    List<Timetable> findByEventIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long eventId);
}
