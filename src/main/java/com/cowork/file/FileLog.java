package com.cowork.file;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 파일 변경 이력(FileLog) 엔티티
 *
 * 역할:
 *   파일/폴더(FileItem) 에 대한 모든 변경 작업(업로드·이름변경·이동·삭제)을 감사(audit) 목적으로 기록한다.
 *   누가 언제 어떤 작업을 했는지 추적할 수 있다.
 *
 * 관계:
 *   - FileLog N : 1 FileItem  (file_item_id FK)
 *
 * 작업 유형 (FileLogAction):
 *   - UPLOAD  : 파일 업로드
 *   - RENAME  : 이름 변경
 *   - MOVE    : 폴더 이동
 *   - DELETE  : 삭제
 *
 * 사용 시점:
 *   - 파일 업로드·수정·이동·삭제 시 FileService 에서 자동으로 로그 생성.
 *   - GET /api/files/{id} 응답의 logs 리스트에 포함되어 반환됨.
 */
@Entity
@Table(name = "file_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FileLog {

    /** PK: auto increment */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 변경된 파일/폴더 ID */
    @Column(name = "file_item_id", nullable = false)
    private Long fileItemId;

    /** 수행된 작업 유형 (UPLOAD / RENAME / MOVE / DELETE) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FileLogAction action;

    /** 작업을 수행한 사용자 ID */
    @Column(name = "actor_id")
    private Long actorId;

    /** 작업을 수행한 사용자 이름 (사용자 삭제 후에도 이력 보존용) */
    @Column(name = "actor_name", length = 50)
    private String actorName;

    /**
     * 작업 상세 정보 (JSON 객체)
     * - RENAME : {"oldName": "이전이름.pdf", "newName": "새이름.pdf"}
     * - MOVE   : {"fromParentId": 3, "toParentId": 7}
     * - 자유 형식 Map 으로 저장.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    private Map<String, Object> detail;

    /** 로그 생성 일시 (변경 불가) */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** JPA 최초 저장 직전 — createdAt 초기화 */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
