package com.cowork.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CoworkEventRepository extends JpaRepository<CoworkEvent, Long> {

    @Query("SELECT e FROM CoworkEvent e WHERE e.cohortId = :cohortId AND e.deletedAt IS NULL " +
           "AND (:status IS NULL OR e.status = :status) " +
           "AND (:category IS NULL OR e.category = :category) ORDER BY e.startDate DESC")
    List<CoworkEvent> findFiltered(Long cohortId, EventStatus status, String category);
}
