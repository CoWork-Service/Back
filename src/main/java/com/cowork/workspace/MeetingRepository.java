package com.cowork.workspace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    List<Meeting> findByWorkspaceIdAndDeletedAtIsNullOrderByDateDesc(Long workspaceId);

    long countByWorkspaceIdAndDeletedAtIsNull(Long workspaceId);
}
