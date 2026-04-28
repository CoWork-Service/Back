package com.cowork.file;

import com.cowork.cohort.Department;
import com.cowork.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "file_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FileItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cohort_id", nullable = false)
    private Long cohortId;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FileType type;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column
    private Long size;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "storage_path", length = 500)
    private String storagePath;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Department department;

    @Column(name = "uploaded_by", length = 50)
    private String uploadedBy;

    @Column(name = "event_id")
    private Long eventId;

    public void rename(String newName) {
        this.name = newName;
    }

    public void move(Long newParentId) {
        this.parentId = newParentId;
    }

    public void updateDepartment(Department department) {
        this.department = department;
    }
}
