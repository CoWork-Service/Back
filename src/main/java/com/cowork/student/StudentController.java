package com.cowork.student;

import com.cowork.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "Student", description = "학생 명단 관리 API — 학생 CRUD, 엑셀 일괄 업로드, 회비 납부 상태 관리")
@SecurityRequirement(name = "Bearer Authentication")
@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    @Operation(
            summary = "학생 목록 조회",
            description = """
                    코호트의 학생 명단을 조회합니다.

                    **사용 시점:** 학생 명단 화면에서 목록 표시 및 필터 적용 시.

                    **필터 옵션:**
                    - `grade`: 학년 (1 / 2 / 3 / 4)
                    - `paymentStatus`: `PAID`(납부) / `UNPAID`(미납)
                    - `search`: 이름 또는 학번으로 검색
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "학생 목록 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": [
                                        {
                                          "id": 1,
                                          "cohortId": 5,
                                          "studentNumber": "2023001",
                                          "name": "홍길동",
                                          "department": "컴퓨터공학과",
                                          "grade": 2,
                                          "paymentStatus": "PAID",
                                          "paidAt": "2025-03-05T10:00:00",
                                          "note": null,
                                          "createdAt": "2025-03-01T09:00:00",
                                          "updatedAt": "2025-03-05T10:00:00"
                                        },
                                        {
                                          "id": 2,
                                          "cohortId": 5,
                                          "studentNumber": "2023002",
                                          "name": "이철수",
                                          "department": "소프트웨어학과",
                                          "grade": 2,
                                          "paymentStatus": "UNPAID",
                                          "paidAt": null,
                                          "note": "연락 필요",
                                          "createdAt": "2025-03-01T09:00:00",
                                          "updatedAt": "2025-03-01T09:00:00"
                                        }
                                      ],
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<StudentResponse>>> getStudents(
            @Parameter(description = "코호트 ID (필수)", required = true, example = "5") @RequestParam Long cohortId,
            @Parameter(description = "학년 필터 (1~4)", example = "2") @RequestParam(required = false) Integer grade,
            @Parameter(description = "납부 상태 필터 (PAID / UNPAID)", example = "UNPAID") @RequestParam(required = false) PaymentStatus paymentStatus,
            @Parameter(description = "이름 또는 학번 검색어", example = "홍길") @RequestParam(required = false) String search) {
        List<StudentResponse> list = studentService.getStudents(cohortId, grade, paymentStatus, search)
                .stream().map(StudentResponse::of).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @Operation(
            summary = "학생 요약 통계 조회",
            description = """
                    코호트의 학생 인원 집계 데이터를 반환합니다.

                    **사용 시점:** 대시보드 카드나 요약 패널에서 수치를 표시할 때.

                    **응답 구조:**
                    ```json
                    {
                      "total": 총학생수,
                      "paid": 납부완료수,
                      "unpaid": 미납수
                    }
                    ```
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "학생 통계 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "total": 52,
                                        "paid": 38,
                                        "unpaid": 14
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary(
            @Parameter(description = "코호트 ID (필수)", required = true, example = "5") @RequestParam Long cohortId) {
        return ResponseEntity.ok(ApiResponse.ok(studentService.getSummary(cohortId)));
    }

    @Operation(
            summary = "학생 개별 등록",
            description = """
                    새 학생을 등록합니다.

                    **사용 시점:** 관리자가 학생을 한 명씩 수동 등록할 때.

                    같은 코호트 내에서 학번(`studentNumber`)이 중복되면 예외가 발생합니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "학생 등록 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "id": 55,
                                        "cohortId": 5,
                                        "studentNumber": "2025001",
                                        "name": "박민준",
                                        "department": "컴퓨터공학과",
                                        "grade": 1,
                                        "paymentStatus": "UNPAID",
                                        "paidAt": null,
                                        "note": null,
                                        "createdAt": "2025-05-10T10:00:00",
                                        "updatedAt": "2025-05-10T10:00:00"
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "학번 중복",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "data": null,
                                      "message": "이미 등록된 학번입니다.",
                                      "code": "STUDENT_NUMBER_DUPLICATE"
                                    }
                                    """)))
    })
    @PostMapping
    public ResponseEntity<ApiResponse<StudentResponse>> createStudent(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "학생 등록 요청",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "cohortId": 5,
                              "studentNumber": "2025001",
                              "name": "박민준",
                              "department": "컴퓨터공학과",
                              "grade": 1,
                              "paymentStatus": "UNPAID",
                              "paidAt": null,
                              "note": null
                            }
                            """)))
            @RequestBody StudentRequest request) {
        Student student = studentService.createStudent(
                request.getCohortId(), request.getStudentNumber(), request.getName(),
                request.getDepartment(), request.getGrade(), request.getPaymentStatus(),
                request.getPaidAt(), request.getNote()
        );
        return ResponseEntity.ok(ApiResponse.ok(StudentResponse.of(student)));
    }

    @Operation(
            summary = "학생 일괄 업로드 (엑셀 임포트)",
            description = """
                    엑셀 파일(.xlsx)로 학생 명단을 일괄 업로드합니다.

                    **사용 시점:** 학기 초 대량 명단을 엑셀로 한번에 업로드할 때.

                    **요청 형식:** `multipart/form-data`

                    **처리 방식:**
                    - 학번(`studentNumber`) 기준으로 기존 레코드가 있으면 **UPDATE**
                    - 없으면 **INSERT**

                    **엑셀 파일 형식 (컬럼 순서):**
                    | 학번 | 이름 | 학과 | 학년 | 납부여부 |
                    |------|------|------|------|----------|

                    **응답:** 처리 결과(총 건수, 생성 수, 수정 수)와 전체 학생 목록
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "일괄 업로드 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "total": 52,
                                        "created": 48,
                                        "updated": 4,
                                        "students": [ { "id": 1, "studentNumber": "2025001", "name": "박민준", "..." : "..." } ]
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """)))
    })
    @PostMapping("/import")
    public ResponseEntity<ApiResponse<StudentImportResponse>> importStudents(
            @Parameter(description = "코호트 ID", required = true, example = "5") @RequestParam Long cohortId,
            @Parameter(description = "엑셀 파일 (.xlsx)", required = true) @RequestParam("file") MultipartFile file) {
        StudentService.StudentImportResult result = studentService.importStudents(cohortId, file);
        return ResponseEntity.ok(ApiResponse.ok(StudentImportResponse.of(result)));
    }

    @Operation(
            summary = "학생 단건 조회",
            description = """
                    학생 ID로 단일 학생 상세 정보를 조회합니다.

                    **사용 시점:** 학생 상세 화면 또는 수정 폼 진입 시.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "학생 조회 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "id": 1,
                                        "cohortId": 5,
                                        "studentNumber": "2023001",
                                        "name": "홍길동",
                                        "department": "컴퓨터공학과",
                                        "grade": 2,
                                        "paymentStatus": "PAID",
                                        "paidAt": "2025-03-05T10:00:00",
                                        "note": null,
                                        "createdAt": "2025-03-01T09:00:00",
                                        "updatedAt": "2025-03-05T10:00:00"
                                      },
                                      "message": null,
                                      "code": null
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "학생을 찾을 수 없음")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StudentResponse>> getStudent(
            @Parameter(description = "학생 ID", required = true, example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(StudentResponse.of(studentService.getStudent(id))));
    }

    @Operation(
            summary = "학생 정보 수정",
            description = """
                    학생의 정보를 수정합니다.

                    **사용 시점:** 학생 정보 편집 폼 저장 시.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "학생 수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "학생을 찾을 수 없음")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<StudentResponse>> updateStudent(
            @Parameter(description = "학생 ID", required = true, example = "1") @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "학생 수정 요청 (cohortId 제외)",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "studentNumber": "2023001",
                              "name": "홍길동",
                              "department": "컴퓨터공학과",
                              "grade": 3,
                              "paymentStatus": "PAID",
                              "paidAt": "2025-03-05T10:00:00",
                              "note": "회장"
                            }
                            """)))
            @RequestBody StudentRequest request) {
        Student student = studentService.updateStudent(
                id, request.getStudentNumber(), request.getName(), request.getDepartment(),
                request.getGrade(), request.getPaymentStatus(), request.getPaidAt(), request.getNote()
        );
        return ResponseEntity.ok(ApiResponse.ok(StudentResponse.of(student)));
    }

    @Operation(
            summary = "학생 삭제",
            description = """
                    학생 레코드를 삭제합니다.

                    **사용 시점:** 전입·탈퇴 등으로 명단에서 제거할 때.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "학생 삭제 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "success": true, "data": null, "message": null, "code": null }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "학생을 찾을 수 없음")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStudent(
            @Parameter(description = "학생 ID", required = true, example = "1") @PathVariable Long id) {
        studentService.deleteStudent(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @Operation(
            summary = "회비 납부 상태 일괄 변경",
            description = """
                    복수의 학생에 대해 회비 납부 상태를 일괄 변경합니다.

                    **사용 시점:** 체크박스로 여러 학생을 선택한 후 일괄 납부 처리할 때.

                    **처리 방식:**
                    - `PAID`로 변경 → `paidAt`을 현재 시각으로 설정
                    - `UNPAID`로 변경 → `paidAt`을 null로 초기화
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "일괄 납부 상태 변경 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    { "success": true, "data": null, "message": null, "code": null }
                                    """)))
    })
    @PatchMapping("/bulk-payment")
    public ResponseEntity<ApiResponse<Void>> bulkPayment(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "일괄 납부 상태 변경 요청",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = """
                            {
                              "ids": [1, 2, 5, 8],
                              "paymentStatus": "PAID"
                            }
                            """)))
            @RequestBody BulkPaymentRequest request) {
        studentService.bulkUpdatePayment(request.getIds(), request.getPaymentStatus());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ─── 요청/응답 DTOs ───────────────────────────────────────────────────────

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

    @Getter
    static class BulkPaymentRequest {
        private List<Long> ids;
        private PaymentStatus paymentStatus;
    }

    record StudentResponse(Long id, Long cohortId, String studentNumber, String name, String department,
                           Integer grade, String paymentStatus, LocalDateTime paidAt, String note,
                           LocalDateTime createdAt, LocalDateTime updatedAt) {
        static StudentResponse of(Student student) {
            return new StudentResponse(
                    student.getId(), student.getCohortId(), student.getStudentNumber(),
                    student.getName(), student.getDepartment(), student.getGrade(),
                    student.getPaymentStatus().name(), student.getPaidAt(), student.getNote(),
                    student.getCreatedAt(), student.getUpdatedAt()
            );
        }
    }

    record StudentImportResponse(int total, int created, int updated, List<StudentResponse> students) {
        static StudentImportResponse of(StudentService.StudentImportResult result) {
            return new StudentImportResponse(
                    result.total(), result.created(), result.updated(),
                    result.students().stream().map(StudentResponse::of).collect(Collectors.toList())
            );
        }
    }
}
