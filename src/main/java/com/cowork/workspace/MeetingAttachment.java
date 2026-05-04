package com.cowork.workspace;

import jakarta.persistence.*;
import lombok.*;

/**
 * 회의록 첨부파일(MeetingAttachment) 엔티티
 *
 * 역할:
 *   회의록(Meeting) 에 연결된 첨부 파일 메타정보를 저장한다.
 *   실제 파일 바이너리는 스토리지에 저장되며, 이 엔티티는 파일 참조 정보만 보관한다.
 *
 * 관계:
 *   - MeetingAttachment N : 1 Meeting   (meeting_id FK)
 *   - file_item_id 로 FileItem 과 연결 가능 (선택, 파일 관리 모듈과 공유 시)
 *
 * 사용 시점:
 *   - 회의록 생성·수정 시 attachments 목록을 함께 전달하면
 *     WorkspaceService 가 기존 첨부파일을 교체(삭제 후 재삽입)한다.
 *   - 회의록 상세 조회 시 attachments 리스트로 반환.
 */
@Entity
@Table(name = "meeting_attachments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MeetingAttachment {

    /** PK: auto increment */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 회의록 ID */
    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    /**
     * 파일 관리 모듈의 FileItem ID (선택)
     * - 파일 관리 모듈에서 업로드한 파일을 첨부할 때 연결.
     * - 직접 업로드한 경우 null 일 수 있음.
     */
    @Column(name = "file_item_id")
    private Long fileItemId;

    /** 스토리지 내 파일 경로 (직접 업로드된 경우 설정) */
    @Column(name = "storage_path", length = 500)
    private String storagePath;

    /** 파일 이름 (화면에 표시될 이름) */
    @Column(nullable = false, length = 255)
    private String name;

    /** 파일 크기 (바이트, 선택) */
    @Column
    private Long size;
}
