package com.cowork.common.storage;

import com.cowork.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "stored_files",
        indexes = {
                @Index(name = "idx_stored_files_storage_path", columnList = "storage_path"),
                @Index(name = "idx_stored_files_module_cohort", columnList = "module, cohort_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class StoredFile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "storage_type", nullable = false, length = 20)
    private String storageType;

    @Column(name = "bucket", length = 100)
    private String bucket;

    @Column(name = "object_key", nullable = false, length = 700)
    private String objectKey;

    @Column(name = "storage_path", nullable = false, unique = true, length = 500)
    private String storagePath;

    @Column(name = "module", nullable = false, length = 50)
    private String module;

    @Column(name = "cohort_id")
    private Long cohortId;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "content_type", length = 150)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;
}
