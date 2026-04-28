package com.cowork.file;

import com.cowork.cohort.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FileItemRepository extends JpaRepository<FileItem, Long> {

    @Query("SELECT f FROM FileItem f WHERE f.cohortId = :cohortId AND f.deletedAt IS NULL " +
           "AND ((:parentId IS NULL AND f.parentId IS NULL) OR f.parentId = :parentId) " +
           "AND (:department IS NULL OR f.department = :department) " +
           "ORDER BY f.type DESC, f.name ASC")
    List<FileItem> findFiltered(Long cohortId, Long parentId, Department department);

    List<FileItem> findByCohortIdAndDeletedAtIsNull(Long cohortId);

    long countByCohortIdAndDeletedAtIsNull(Long cohortId);

    long countByCohortIdAndDepartmentAndDeletedAtIsNull(Long cohortId, Department department);
}
