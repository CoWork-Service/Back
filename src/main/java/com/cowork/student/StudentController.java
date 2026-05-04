package com.cowork.student;

import com.cowork.common.ApiResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 학생 관리 컨트롤러 (StudentController)
 *
 * 역할:
 *   코호트별 학생 명단과 회비 납부 상태를 관리하는 API 를 제공한다.
 *   기본 경로: /api/students
 *
 * 주요 기능:
 *   - 학생 CRUD (개별 등록·조회·수정·삭제)
 *   - 엑셀 파일로 일괄 업로드 (import)
 *   - 회비 납부 상태 일괄 변경 (bulkPayment)
 *   - 요약 통계 조회 (총 인원, 납부/미납 수 등)
 *
 * 인증 필요: 모든 엔드포인트에 JWT Access Token 필요
 */
@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    /**
     * 학생 목록 조회
     *
     * 동작: cohortId 기준으로 학생 목록을 조회하며, 학년·납부상태·이름 검색으로 필터링 가능.
     * 사용 시점: 학생 명단 화면에서 목록 표시 및 필터 적용 시.
     *
     * @param cohortId      필수. 조회할 코호트 ID
     * @param grade         선택. 학년 필터 (1~4)
     * @param paymentStatus 선택. 납부 상태 필터 (PAID / UNPAID)
     * @param search        선택. 이름 또는 학번 검색어
     * @return 학생 목록
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<StudentResponse>>> getStudents(
            @RequestParam Long cohortId,
            @RequestParam(required = false) Integer grade,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) String search) {
        List<StudentResponse> list = studentService.getStudents(cohortId, grade, paymentStatus, search)
                .stream()
                .map(StudentResponse::of)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    /**
     * 학생 요약 통계 조회
     *
     * 동작: 특정 코호트의 총 학생 수, 납부 완료 수, 미납 수 등 집계 데이터를 반환.
     * 사용 시점: 대시보드 카드나 요약 패널에서 수치를 표시할 때.
     *
     * @param cohortId 조회할 코호트 ID
     * @return { "total": 50, "paid": 30, "unpaid": 20 } 형태의 집계 맵
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary(@RequestParam Long cohortId) {
        return ResponseEntity.ok(ApiResponse.ok(studentService.getSummary(cohortId)));
    }

    /**
     * 학생 개별 등록
     *
     * 동작: 새 학생 레코드를 생성한다.
     *       같은 코호트 내에서 학번(studentNumber)이 중복되면 예외 발생.
     * 사용 시점: 관리자가 학생을 한 명씩 수동 등록할 때.
     *
     * @param request 학생 정보 (cohortId, studentNumber, name, department, grade, paymentStatus, paidAt, note)
     * @return 생성된 학생 정보
     */
    @PostMapping
    public ResponseEntity<ApiResponse<StudentResponse>> createStudent(@RequestBody StudentRequest request) {
        Student student = studentService.createStudent(
                request.getCohortId(),
                request.getStudentNumber(),
                request.getName(),
                request.getDepartment(),
                request.getGrade(),
                request.getPaymentStatus(),
                request.getPaidAt(),
                request.getNote()
        );
        return ResponseEntity.ok(ApiResponse.ok(StudentResponse.of(student)));
    }

    /**
     * 학생 일괄 업로드 (엑셀 임포트)
     *
     * 동작:
     *   1. 업로드된 .xlsx 파일을 파싱하여 학생 목록 추출.
     *   2. 학번 기준으로 기존 레코드가 있으면 UPDATE, 없으면 INSERT.
     *   3. 처리 결과(총 건수, 생성 수, 수정 수)와 전체 학생 목록을 반환.
     *
     * 사용 시점: 학기 초 대량 명단을 엑셀로 한번에 업로드할 때.
     *
     * Content-Type: multipart/form-data
     * @param cohortId 대상 코호트 ID (쿼리 파라미터)
     * @param file     엑셀 파일 (.xlsx)
     * @return { total, created, updated, students }
     */
    @PostMapping("/import")
    public ResponseEntity<ApiResponse<StudentImportResponse>> importStudents(
            @RequestParam Long cohortId,
            @RequestParam("file") MultipartFile file) {
        StudentService.StudentImportResult result = studentService.importStudents(cohortId, file);
        return ResponseEntity.ok(ApiResponse.ok(StudentImportResponse.of(result)));
    }

    /**
     * 학생 단건 조회
     *
     * 동작: 학생 ID 로 단일 학생 상세 정보를 반환.
     * 사용 시점: 학생 상세 화면 또는 수정 폼 진입 시.
     *
     * @param id 학생 ID
     * @return 학생 상세 정보
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StudentResponse>> getStudent(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(StudentResponse.of(studentService.getStudent(id))));
    }

    /**
     * 학생 정보 수정
     *
     * 동작: 지정한 학생의 학번·이름·학과·학년·납부상태·메모를 수정한다.
     * 사용 시점: 학생 정보 편집 폼 저장 시.
     *
     * @param id      학생 ID
     * @param request 수정할 정보
     * @return 수정된 학생 정보
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<StudentResponse>> updateStudent(
            @PathVariable Long id,
            @RequestBody StudentRequest request) {
        Student student = studentService.updateStudent(
                id,
                request.getStudentNumber(),
                request.getName(),
                request.getDepartment(),
                request.getGrade(),
                request.getPaymentStatus(),
                request.getPaidAt(),
                request.getNote()
        );
        return ResponseEntity.ok(ApiResponse.ok(StudentResponse.of(student)));
    }

    /**
     * 학생 삭제
     *
     * 동작: 지정한 학생 레코드를 삭제한다.
     * 사용 시점: 전입·탈퇴 등으로 명단에서 제거할 때.
     *
     * @param id 학생 ID
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStudent(@PathVariable Long id) {
        studentService.deleteStudent(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * 회비 납부 상태 일괄 변경
     *
     * 동작: 복수의 학생 ID 목록에 대해 paymentStatus 를 한꺼번에 변경한다.
     *       PAID → paidAt 을 현재 시각으로 설정, UNPAID → paidAt 을 null 로 초기화.
     * 사용 시점: 체크박스로 여러 학생을 선택한 후 일괄 납부 처리할 때.
     *
     * @param request { "ids": [1, 2, 3], "paymentStatus": "PAID" }
     */
    @PatchMapping("/bulk-payment")
    public ResponseEntity<ApiResponse<Void>> bulkPayment(@RequestBody BulkPaymentRequest request) {
        studentService.bulkUpdatePayment(request.getIds(), request.getPaymentStatus());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ─── 요청/응답 DTOs ───────────────────────────────────────────────────────

    /** POST/PUT 학생 요청 바디 */
    @Getter
    static class StudentRequest {
        private Long cohortId;
        private String studentNumber;
        private String name;
        private String department;
        private Integer grade;
        private PaymentStatus paymentStatus;
        private LocalDateTime paidAt;
        private String note;
    }

    /** PATCH /bulk-payment 요청 바디 */
    @Getter
    static class BulkPaymentRequest {
        private List<Long> ids;
        private PaymentStatus paymentStatus;
    }

    /** 학생 단일 응답 DTO */
    record StudentResponse(Long id, Long cohortId, String studentNumber, String name, String department,
                           Integer grade, String paymentStatus, LocalDateTime paidAt, String note,
                           LocalDateTime createdAt, LocalDateTime updatedAt) {
        static StudentResponse of(Student student) {
            return new StudentResponse(
                    student.getId(),
                    student.getCohortId(),
                    student.getStudentNumber(),
                    student.getName(),
                    student.getDepartment(),
                    student.getGrade(),
                    student.getPaymentStatus().name(),
                    student.getPaidAt(),
                    student.getNote(),
                    student.getCreatedAt(),
                    student.getUpdatedAt()
            );
        }
    }

    /** 일괄 업로드 결과 응답 DTO */
    record StudentImportResponse(int total, int created, int updated, List<StudentResponse> students) {
        static StudentImportResponse of(StudentService.StudentImportResult result) {
            return new StudentImportResponse(
                    result.total(),
                    result.created(),
                    result.updated(),
                    result.students().stream().map(StudentResponse::of).collect(Collectors.toList())
            );
        }
    }
}
