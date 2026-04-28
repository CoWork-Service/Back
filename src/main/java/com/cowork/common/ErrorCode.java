package com.cowork.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Auth
    INVALID_CREDENTIALS(400, "이메일 또는 비밀번호가 올바르지 않습니다"),
    INVALID_TOKEN(401, "유효하지 않은 토큰입니다"),
    EXPIRED_TOKEN(401, "만료된 토큰입니다"),
    ACCESS_DENIED(403, "접근 권한이 없습니다"),
    INVALID_INVITE_CODE(400, "유효하지 않은 초대 코드입니다"),
    PENDING_APPROVAL(403, "관리자 승인 대기 중입니다"),
    DUPLICATE_EMAIL(409, "이미 사용 중인 이메일입니다"),

    // General
    NOT_FOUND(404, "리소스를 찾을 수 없습니다"),
    DUPLICATE_KEY(409, "이미 존재하는 데이터입니다"),
    VALIDATION_FAILED(400, "입력값이 올바르지 않습니다"),
    STORAGE_ERROR(500, "파일 저장 중 오류가 발생했습니다"),
    INTERNAL_ERROR(500, "서버 오류가 발생했습니다"),

    // Memo
    MEMO_NOT_FOUND(404, "메모를 찾을 수 없습니다"),

    // File
    FILE_NOT_FOUND(404, "파일을 찾을 수 없습니다"),

    // Budget
    EXPENSE_NOT_FOUND(404, "지출 내역을 찾을 수 없습니다"),

    // Asset
    ASSET_NOT_FOUND(404, "자산을 찾을 수 없습니다"),
    ASSET_UNAVAILABLE(400, "대여 가능한 수량이 없습니다"),
    RENTAL_NOT_FOUND(404, "대여 기록을 찾을 수 없습니다"),

    // Student
    STUDENT_NOT_FOUND(404, "학생을 찾을 수 없습니다"),
    STUDENT_DUPLICATE(409, "이미 등록된 학번입니다"),

    // Survey
    SURVEY_NOT_FOUND(404, "설문을 찾을 수 없습니다"),
    SURVEY_CLOSED(400, "마감된 설문에는 응답할 수 없습니다"),
    SURVEY_NOT_OPEN(400, "공개되지 않은 설문입니다"),

    // Workspace
    WORKSPACE_NOT_FOUND(404, "워크스페이스를 찾을 수 없습니다"),
    MEETING_NOT_FOUND(404, "회의록을 찾을 수 없습니다"),

    // Schedule
    TIMETABLE_NOT_FOUND(404, "시간 조율을 찾을 수 없습니다"),
    TIMETABLE_CLOSED(400, "마감된 시간 조율입니다"),

    // Event
    EVENT_NOT_FOUND(404, "행사를 찾을 수 없습니다"),
    PHOTO_NOT_FOUND(404, "사진을 찾을 수 없습니다"),

    // Mobile Session
    MOBILE_SESSION_NOT_FOUND(404, "모바일 세션을 찾을 수 없습니다"),
    MOBILE_SESSION_EXPIRED(400, "만료된 세션입니다. 다시 시도해 주세요"),
    MOBILE_SESSION_USED(400, "이미 사용된 세션입니다"),

    // Cohort
    COHORT_NOT_FOUND(404, "기수를 찾을 수 없습니다"),

    // Organization
    ORGANIZATION_NOT_FOUND(404, "학생회를 찾을 수 없습니다"),

    // User
    USER_NOT_FOUND(404, "사용자를 찾을 수 없습니다");

    private final int status;
    private final String message;
}
