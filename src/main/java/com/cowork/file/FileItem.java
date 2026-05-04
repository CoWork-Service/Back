package com.cowork.file;

import com.cowork.cohort.Department;
import com.cowork.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 파일 아이템(FileItem) 엔티티
 *
 * 역할:
 *   코호트의 파일/폴더 목록을 트리 구조로 관리한다.
 *   FOLDER 타입은 하위 항목의 컨테이너 역할, FILE 타입은 실제 업로드된 파일을 나타낸다.
 *
 * 관계:
 *   - FileItem N : 1 FileItem (self-join, parent_id 로 트리 구성)
 *   - FileItem 1 : N FileLog  (file_logs 테이블의 file_item_id FK)
 *   - cohort_id 로 코호트와 연결
 *
 * 파일 타입 (FileType):
 *   - FOLDER : 폴더 (storagePath, mimeType, size 없음)
 *   - FILE   : 실제 파일
 *
 * 사용 시점:
 *   - 폴더 생성: POST /api/files/folder
 *   - 파일 업로드: POST /api/files/upload (multipart)
 *   - 파일/폴더 목록 조회: GET /api/files?cohortId=&parentId= (트리 탐색)
 *   - 이름 변경: PUT /api/files/{id}
 *   - 폴더 이동: PATCH /api/files/{id}/move
 *   - 다운로드: GET /api/files/{id}/download
 */
@Entity
@Table(name = "file_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FileItem extends BaseEntity {

    /** PK: auto increment */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 코호트 ID */
    @Column(name = "cohort_id", nullable = false)
    private Long cohortId;

    /** 파일 또는 폴더 이름 (확장자 포함) */
    @Column(nullable = false, length = 255)
    private String name;

    /**
     * 항목 타입
     * - FILE   : 실제 업로드된 파일
     * - FOLDER : 폴더
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FileType type;

    /** MIME 타입 (예: "image/png", "application/pdf"), 폴더이면 null */
    @Column(name = "mime_type", length = 100)
    private String mimeType;

    /** 파일 크기 (바이트), 폴더이면 null */
    @Column
    private Long size;

    /**
     * 부모 폴더 ID (self-join)
     * - null 이면 루트 레벨 항목.
     * - parentId 로 재귀적인 폴더 트리를 구성할 수 있다.
     */
    @Column(name = "parent_id")
    private Long parentId;

    /** 스토리지 내 파일 경로 (폴더이면 null) */
    @Column(name = "storage_path", length = 500)
    private String storagePath;

    /** 담당 부서 (null 이면 전체 공용) */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Department department;

    /** 업로드한 사람 이름 (로그인 사용자 이름 저장) */
    @Column(name = "uploaded_by", length = 50)
    private String uploadedBy;

    /** 연결된 행사 ID (선택) */
    @Column(name = "event_id")
    private Long eventId;

    /**
     * 파일/폴더 이름 변경
     *
     * 동작: name 을 새 이름으로 교체.
     * 사용 시점: FileService.updateFile() 에서 호출 (PUT /api/files/{id}).
     *
     * @param newName 새 이름 (확장자 포함)
     */
    public void rename(String newName) {
        this.name = newName;
    }

    /**
     * 폴더 이동
     *
     * 동작: parentId 를 새 부모 폴더 ID 로 변경.
     * 사용 시점: FileService.moveFile() 에서 호출 (PATCH /api/files/{id}/move).
     *
     * @param newParentId 이동할 대상 폴더 ID (null 이면 루트로 이동)
     */
    public void move(Long newParentId) {
        this.parentId = newParentId;
    }

    /**
     * 담당 부서 변경
     *
     * 동작: department 를 새 부서로 교체.
     * 사용 시점: FileService.updateFile() 에서 부서 재지정 시 호출.
     *
     * @param department 새 부서 (null 이면 전체 공용)
     */
    public void updateDepartment(Department department) {
        this.department = department;
    }
}
