package com.cowork.schedule;

import com.cowork.common.BusinessException;
import com.cowork.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final TimetableRepository timetableRepository;
    private final TimetableParticipantRepository participantRepository;
    private final TimetableSubmissionRepository submissionRepository;

    public List<Timetable> getTimetables(Long cohortId, TimetableStatus status) {
        return timetableRepository.findFiltered(cohortId, status);
    }

    public long getResponseCount(Long timetableId) {
        return submissionRepository.findByTimetableIdOrderBySubmittedAtDesc(timetableId).size();
    }

    public long getParticipantCount(Long timetableId) {
        return participantRepository.countByTimetableId(timetableId);
    }

    public TimetableDetail getTimetableDetail(Long timetableId) {
        Timetable timetable = findTimetable(timetableId);
        return buildDetail(timetable);
    }

    @Transactional
    public TimetableDetail createTimetable(Long cohortId, String title, String description,
                                           LocalDate dateRangeStart, LocalDate dateRangeEnd,
                                           LocalTime timeRangeStart, LocalTime timeRangeEnd,
                                           Integer slotMinutes, TimetableStatus status,
                                           Long createdBy, Long eventId, List<String> participants) {
        Timetable timetable = Timetable.builder()
                .cohortId(cohortId)
                .title(title)
                .description(description)
                .dateRangeStart(dateRangeStart)
                .dateRangeEnd(dateRangeEnd)
                .timeRangeStart(timeRangeStart)
                .timeRangeEnd(timeRangeEnd)
                .slotMinutes(slotMinutes)
                .status(status != null ? status : TimetableStatus.OPEN)
                .createdBy(createdBy)
                .eventId(eventId)
                .build();
        timetableRepository.save(timetable);
        syncParticipants(timetable.getId(), participants);
        return buildDetail(timetable);
    }

    @Transactional
    public TimetableDetail updateTimetable(Long timetableId, String title, String description,
                                           LocalDate dateRangeStart, LocalDate dateRangeEnd,
                                           LocalTime timeRangeStart, LocalTime timeRangeEnd,
                                           Integer slotMinutes, TimetableStatus status,
                                           Long eventId, List<String> participants) {
        Timetable timetable = findTimetable(timetableId);
        timetable.update(title, description, dateRangeStart, dateRangeEnd, timeRangeStart, timeRangeEnd, slotMinutes, eventId);
        if (status != null) {
            timetable.updateStatus(status);
        }
        syncParticipants(timetableId, participants);
        return buildDetail(timetable);
    }

    @Transactional
    public Timetable updateStatus(Long timetableId, TimetableStatus status) {
        Timetable timetable = findTimetable(timetableId);
        timetable.updateStatus(status);
        return timetable;
    }

    @Transactional
    public void deleteTimetable(Long timetableId) {
        findTimetable(timetableId).softDelete();
    }

    @Transactional
    public TimetableSubmission respond(Long timetableId, String participantName, List<TimeSlotPayload> slots) {
        Timetable timetable = findTimetable(timetableId);
        if (timetable.getStatus() == TimetableStatus.CLOSED) {
            throw new BusinessException(ErrorCode.TIMETABLE_CLOSED);
        }

        TimetableParticipant participant = participantRepository.findByTimetableIdAndName(timetableId, participantName)
                .orElseGet(() -> participantRepository.save(TimetableParticipant.builder()
                        .timetableId(timetableId)
                        .name(participantName)
                        .build()));
        participant.markResponded();

        List<String> selectedSlots = slots == null
                ? List.of()
                : slots.stream()
                .filter(slot -> StringUtils.hasText(slot.date()) && StringUtils.hasText(slot.time()))
                .map(slot -> slot.date() + " " + slot.time())
                .toList();

        TimetableSubmission submission = submissionRepository.findByTimetableIdAndParticipantId(timetableId, participant.getId())
                .orElse(null);
        if (submission == null) {
            submission = TimetableSubmission.builder()
                    .timetableId(timetableId)
                    .participantId(participant.getId())
                    .selectedSlots(selectedSlots)
                    .build();
            return submissionRepository.save(submission);
        }

        submission.updateSelectedSlots(selectedSlots);
        return submission;
    }

    public TimetableResult getResults(Long timetableId) {
        TimetableDetail detail = getTimetableDetail(timetableId);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (TimetableSubmission submission : detail.submissions()) {
            for (String slot : submission.getSelectedSlots()) {
                counts.put(slot, counts.getOrDefault(slot, 0L) + 1);
            }
        }

        List<SlotCount> slotCounts = counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    String[] parts = entry.getKey().split(" ", 2);
                    return new SlotCount(parts[0], parts.length > 1 ? parts[1] : "", entry.getValue());
                })
                .collect(Collectors.toList());

        long maxCount = slotCounts.stream().mapToLong(SlotCount::count).max().orElse(0);
        List<SlotCount> bestSlots = slotCounts.stream()
                .filter(slotCount -> slotCount.count() == maxCount)
                .collect(Collectors.toList());

        return new TimetableResult(detail, slotCounts, bestSlots);
    }

    private Timetable findTimetable(Long timetableId) {
        return timetableRepository.findById(timetableId)
                .filter(timetable -> !timetable.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.TIMETABLE_NOT_FOUND));
    }

    private TimetableDetail buildDetail(Timetable timetable) {
        List<TimetableParticipant> participants = participantRepository.findByTimetableIdOrderByCreatedAtAsc(timetable.getId());
        Map<Long, TimetableParticipant> participantsById = participants.stream()
                .collect(Collectors.toMap(TimetableParticipant::getId, participant -> participant));
        List<TimetableSubmission> submissions = submissionRepository.findByTimetableIdOrderBySubmittedAtDesc(timetable.getId());
        return new TimetableDetail(timetable, participants, participantsById, submissions);
    }

    private void syncParticipants(Long timetableId, List<String> desiredNames) {
        List<TimetableParticipant> existing = participantRepository.findByTimetableIdOrderByCreatedAtAsc(timetableId);
        Map<String, TimetableParticipant> existingByName = existing.stream()
                .collect(Collectors.toMap(TimetableParticipant::getName, participant -> participant, (a, b) -> a, LinkedHashMap::new));

        Set<String> desired = desiredNames == null
                ? Set.of()
                : desiredNames.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toSet());

        List<Long> removedIds = existing.stream()
                .filter(participant -> !desired.contains(participant.getName()))
                .map(TimetableParticipant::getId)
                .toList();
        if (!removedIds.isEmpty()) {
            submissionRepository.deleteByParticipantIdIn(removedIds);
            participantRepository.deleteByIdIn(removedIds);
        }

        List<TimetableParticipant> newParticipants = new ArrayList<>();
        if (desiredNames != null) {
            for (String name : desiredNames) {
                if (!StringUtils.hasText(name)) {
                    continue;
                }
                String trimmed = name.trim();
                if (!existingByName.containsKey(trimmed)) {
                    newParticipants.add(TimetableParticipant.builder()
                            .timetableId(timetableId)
                            .name(trimmed)
                            .build());
                }
            }
        }
        if (!newParticipants.isEmpty()) {
            participantRepository.saveAll(newParticipants);
        }
    }

    public record TimeSlotPayload(String date, String time) {
    }

    public record TimetableDetail(Timetable timetable, List<TimetableParticipant> participants,
                                  Map<Long, TimetableParticipant> participantsById,
                                  List<TimetableSubmission> submissions) {
    }

    public record SlotCount(String date, String time, long count) {
    }

    public record TimetableResult(TimetableDetail detail, List<SlotCount> slotCounts,
                                  List<SlotCount> bestSlots) {
    }
}
