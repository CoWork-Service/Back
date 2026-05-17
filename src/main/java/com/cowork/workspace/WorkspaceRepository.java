package com.cowork.workspace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {

    List<Workspace> findByCohortIdOrderByCreatedAtAsc(Long cohortId);

    Optional<Workspace> findByCohortIdAndDepartment(Long cohortId, String department);
}
