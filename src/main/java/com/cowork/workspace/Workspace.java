package com.cowork.workspace;

import com.cowork.cohort.Department;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 워크스페이스(Workspace) 엔티티
 *
 * 역할:
 *   코호트 내에서 파일과 회의록을 관리하는 공간 단위.
 *   부서별 또는 전체 공용 워크스페이스를 구성하여 문서를 체계적으로 정리한다.
 *   예: "기획부 워크스페이스", "전체 공용 자료실"
 *
 * 관계:
 *   - Workspace 1 : N Meeting         (meetings 테이블의 workspace_id FK)
 *   - Workspace 1 : N FileItem        (file_items 테이블 — department 필드로 연결)
 *   - cohort_id 로 코호트와 연결
 *
 * 사용 시점:
 *   - 코호트 생성 시 부서별/전체 워크스페이스가 자동 생성되거나 수동으로 추가.
 *   - 워크스페이스 목록 조회: GET /api/workspaces?cohortId=
 *   - 워크스페이스 이름/설명 수정: PUT /api/workspaces/{id}
 */
@Entity
@Table(name = "workspaces")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Workspace {

    /** PK: auto increment */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 코호트 ID */
    @Column(name = "cohort_id", nullable = false)
    private Long cohortId;

    /**
     * 담당 부서 (null 이면 전체 공용)
     * - 부서별 접근 권한 필터링에 사용 가능.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Department department;

    /** 워크스페이스 이름 (예: "기획부 회의록") */
    @Column(nullable = false, length = 100)
    private String name;

    /** 워크스페이스 설명 (선택) */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 최초 생성 일시 (변경 불가) */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 마지막 수정 일시 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** JPA 최초 저장 직전 — 날짜 필드 초기화 */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /** JPA 업데이트 직전 — updatedAt 갱신 */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 워크스페이스 이름/설명 수정
     *
     * 동작: name 과 description 을 새 값으로 교체.
     * 사용 시점: WorkspaceService.updateWorkspace() 에서 호출 (PUT /api/workspaces/{id}).
     *
     * @param name        새 이름
     * @param description 새 설명
     */
    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
