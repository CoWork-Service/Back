package com.cowork.workspace;

import com.cowork.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "meetings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Meeting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private LocalDate date;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    private List<String> attendees;

    @Column(columnDefinition = "TEXT")
    private String agenda;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "event_id")
    private Long eventId;

    public void update(String title, LocalDate date, List<String> attendees, String agenda, String content, Long eventId) {
        this.title = title;
        this.date = date;
        this.attendees = attendees;
        this.agenda = agenda;
        this.content = content;
        this.eventId = eventId;
    }
}
