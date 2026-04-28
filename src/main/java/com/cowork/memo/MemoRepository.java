package com.cowork.memo;

import com.cowork.cohort.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MemoRepository extends JpaRepository<Memo, Long> {

    @Query("SELECT m FROM Memo m WHERE m.cohortId = :cohortId AND m.deletedAt IS NULL " +
           "AND (:status IS NULL OR m.status = :status) " +
           "AND (:priority IS NULL OR m.priority = :priority) " +
           "AND (:department IS NULL OR m.department = :department) " +
           "ORDER BY m.createdAt DESC")
    List<Memo> findFiltered(Long cohortId, MemoStatus status, MemoPriority priority, Department department);
}
