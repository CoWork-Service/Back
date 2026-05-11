package com.cowork.common.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    Optional<StoredFile> findByStoragePathAndDeletedAtIsNull(String storagePath);
}
