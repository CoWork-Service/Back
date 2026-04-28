package com.cowork.file;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FileLogRepository extends JpaRepository<FileLog, Long> {

    List<FileLog> findByFileItemIdOrderByCreatedAtDesc(Long fileItemId);
}
