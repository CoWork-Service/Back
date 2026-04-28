package com.cowork.workspace;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "meeting_attachments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MeetingAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(name = "file_item_id")
    private Long fileItemId;

    @Column(name = "storage_path", length = 500)
    private String storagePath;

    @Column(nullable = false, length = 255)
    private String name;

    @Column
    private Long size;
}
