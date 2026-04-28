package com.cowork.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

    @Query("SELECT s FROM Student s WHERE s.cohortId = :cohortId AND s.deletedAt IS NULL " +
           "AND (:grade IS NULL OR s.grade = :grade) " +
           "AND (:paymentStatus IS NULL OR s.paymentStatus = :paymentStatus) " +
           "AND (:search IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR s.studentNumber LIKE CONCAT('%', :search, '%')) " +
           "ORDER BY s.studentNumber ASC")
    List<Student> findFiltered(Long cohortId, Integer grade, PaymentStatus paymentStatus, String search);

    List<Student> findByCohortIdAndDeletedAtIsNullOrderByStudentNumberAsc(Long cohortId);

    Optional<Student> findByCohortIdAndStudentNumberAndDeletedAtIsNull(Long cohortId, String studentNumber);

    boolean existsByCohortIdAndStudentNumberAndDeletedAtIsNull(Long cohortId, String studentNumber);
}
