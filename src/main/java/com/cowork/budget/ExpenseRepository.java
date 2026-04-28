package com.cowork.budget;

import com.cowork.cohort.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByCohortIdAndDeletedAtIsNullOrderByDateDesc(Long cohortId);

    @Query("SELECT e FROM Expense e WHERE e.cohortId = :cohortId AND e.deletedAt IS NULL " +
           "AND (:department IS NULL OR e.department = :department) " +
           "AND (:category IS NULL OR e.category = :category) " +
           "AND (:dateFrom IS NULL OR e.date >= :dateFrom) " +
           "AND (:dateTo IS NULL OR e.date <= :dateTo) " +
           "ORDER BY e.date DESC")
    List<Expense> findFiltered(Long cohortId, Department department, String category, LocalDate dateFrom, LocalDate dateTo);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.cohortId = :cohortId AND e.deletedAt IS NULL")
    Long sumAmountByCohortId(Long cohortId);

    List<Expense> findByEventIdAndDeletedAtIsNullOrderByDateDesc(Long eventId);
}
