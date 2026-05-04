package com.cowork.workspace;

import com.cowork.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.List;

/**
 * 회의록(Meeting) 엔티티
 *
 * 역할:
 *   워크스페이스 내에서 진행된 회의의 내용을 기록·보관한다.
 *   안건(agenda), 회의 내용(content), 참석자 목록, 첨부 파일을 함께 관리한다.
 *
 * 관계:
 *   - Meeting N : 1 Workspace           (workspace_id FK)
 *   - Meeting 1 : N MeetingAttachment   (meeting_attachments 테이블의 meeting_id FK)
 *   - event_id 로 특정 행사 회의와 연결 가능 (선택)
 *
 * 사용 시점:
 *   - 회의 후 기록을 남길 때: POST /api/workspaces/{id}/meetings
 *   - 회의록 수정: PUT /api/workspaces/{id}/meetings/{meetingId}
 *   - 삭제: DELETE /api/workspaces/{id}/meetings/{meetingId}
 */
@Entity
@Table(name = "meetings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Meeting extends BaseEntity {

    /** PK: auto increment */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 워크스페이스 ID */
    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    /** 회의 제목 (예: "3월 1차 정기회의") */
    @Column(nullable = false, length = 200)
    private String title;

    /** 회의 날짜 */
    @Column(nullable = false)
    private LocalDate date;

    /**
     * 참석자 목록 (JSON 배열)
     * - 이름 문자열 리스트로 저장. 예: ["홍길동", "이철수", "박영희"]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    private List<String> attendees;

    /** 회의 안건 (사전 공유 내용, 선택) */
    @Column(columnDefinition = "TEXT")
    private String agenda;

    /** 회의 내용/결과 (회의록 본문) */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** 회의록 작성자 사용자 ID */
    @Column(name = "created_by")
    private Long createdBy;

    /** 연결된 행사 ID (행사 관련 회의인 경우 설정, 선택) */
    @Column(name = "event_id")
    private Long eventId;

    /**
     * 회의록 수정
     *
     * 동작: 제목·날짜·참석자·안건·내용·행사ID 를 한꺼번에 업데이트.
     * 사용 시점: WorkspaceService.updateMeeting() 에서 호출.
     */
    public void update(String title, LocalDate date, List<String> attendees, String agenda, String content, Long eventId) {
        this.title = title;
        this.date = date;
        this.attendees = attendees;
        this.agenda = agenda;
        this.content = content;
        this.eventId = eventId;
    }
}
