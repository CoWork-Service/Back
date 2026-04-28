package com.cowork.workspace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MeetingAttachmentRepository extends JpaRepository<MeetingAttachment, Long> {

    List<MeetingAttachment> findByMeetingIdIn(Collection<Long> meetingIds);

    void deleteByMeetingId(Long meetingId);
}
