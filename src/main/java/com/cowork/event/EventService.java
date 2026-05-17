package com.cowork.event;

import com.cowork.budget.Expense;
import com.cowork.budget.ExpenseRepository;
import com.cowork.common.BusinessException;
import com.cowork.common.ErrorCode;
import com.cowork.common.storage.FileStorageService;
import com.cowork.schedule.Timetable;
import com.cowork.schedule.TimetableRepository;
import com.cowork.survey.Survey;
import com.cowork.survey.SurveyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {

    private final CoworkEventRepository eventRepository;
    private final EventPhotoRepository eventPhotoRepository;
    private final ExpenseRepository expenseRepository;
    private final SurveyRepository surveyRepository;
    private final TimetableRepository timetableRepository;
    private final FileStorageService storageService;

    public List<CoworkEvent> getEvents(Long cohortId, EventStatus status) {
        return eventRepository.findFiltered(cohortId, status);
    }

    public EventDetail getEventDetail(Long eventId) {
        CoworkEvent event = findEvent(eventId);
        return new EventDetail(
                event,
                eventPhotoRepository.findByEventIdOrderByUploadedAtDesc(eventId),
                expenseRepository.findByEventIdAndDeletedAtIsNullOrderByDateDesc(eventId),
                surveyRepository.findByEventIdAndDeletedAtIsNullOrderByCreatedAtDesc(eventId),
                timetableRepository.findByEventIdAndDeletedAtIsNullOrderByCreatedAtDesc(eventId)
        );
    }

    @Transactional
    public EventDetail createEvent(Long cohortId, String name, EventStatus status,
                                   String description, LocalDate startDate, LocalDate endDate, String location,
                                   String leadDepartment, List<String> organizers,
                                   Long budget, String coverColor, Long createdBy) {
        CoworkEvent event = CoworkEvent.builder()
                .cohortId(cohortId)
                .name(name)
                .status(status != null ? status : EventStatus.PLANNING)
                .description(description)
                .startDate(startDate)
                .endDate(endDate)
                .location(location)
                .leadDepartment(leadDepartment)
                .organizers(organizers)
                .budget(budget)
                .coverColor(coverColor)
                .createdBy(createdBy)
                .build();
        eventRepository.save(event);
        return getEventDetail(event.getId());
    }

    @Transactional
    public EventDetail updateEvent(Long eventId, String name, EventStatus status,
                                   String description, LocalDate startDate, LocalDate endDate, String location,
                                   String leadDepartment, List<String> organizers,
                                   Long budget, String coverColor) {
        CoworkEvent event = findEvent(eventId);
        event.update(name, status, description, startDate, endDate, location, leadDepartment, organizers, budget, coverColor);
        return getEventDetail(eventId);
    }

    @Transactional
    public void deleteEvent(Long eventId) {
        CoworkEvent event = findEvent(eventId);
        for (EventPhoto photo : eventPhotoRepository.findByEventIdOrderByUploadedAtDesc(eventId)) {
            storageService.delete(photo.getStoragePath());
        }
        event.softDelete();
    }

    @Transactional
    public EventPhoto addPhoto(Long eventId, MultipartFile photo, String caption, String tag, Long uploadedBy) {
        CoworkEvent event = findEvent(eventId);
        String storagePath = storageService.store(photo, "events", event.getCohortId());
        EventPhoto item = EventPhoto.builder()
                .eventId(eventId)
                .storagePath(storagePath)
                .caption(caption)
                .tag(tag != null ? tag : "기타")
                .uploadedBy(uploadedBy)
                .build();
        return eventPhotoRepository.save(item);
    }

    @Transactional
    public void deletePhoto(Long eventId, Long photoId) {
        findEvent(eventId);
        EventPhoto photo = eventPhotoRepository.findById(photoId)
                .filter(item -> item.getEventId().equals(eventId))
                .orElseThrow(() -> new BusinessException(ErrorCode.PHOTO_NOT_FOUND));
        storageService.delete(photo.getStoragePath());
        eventPhotoRepository.delete(photo);
    }

    private CoworkEvent findEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .filter(event -> !event.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));
    }

    public record EventDetail(CoworkEvent event, List<EventPhoto> photos, List<Expense> expenses,
                              List<Survey> surveys, List<Timetable> timetables) {
    }
}
