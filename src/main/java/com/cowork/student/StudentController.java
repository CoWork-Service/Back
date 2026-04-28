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

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

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

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary(@RequestParam Long cohortId) {
        return ResponseEntity.ok(ApiResponse.ok(studentService.getSummary(cohortId)));
    }

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

    @PostMapping("/import")
    public ResponseEntity<ApiResponse<StudentImportResponse>> importStudents(
            @RequestParam Long cohortId,
            @RequestParam("file") MultipartFile file) {
        StudentService.StudentImportResult result = studentService.importStudents(cohortId, file);
        return ResponseEntity.ok(ApiResponse.ok(StudentImportResponse.of(result)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StudentResponse>> getStudent(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(StudentResponse.of(studentService.getStudent(id))));
    }

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

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStudent(@PathVariable Long id) {
        studentService.deleteStudent(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PatchMapping("/bulk-payment")
    public ResponseEntity<ApiResponse<Void>> bulkPayment(@RequestBody BulkPaymentRequest request) {
        studentService.bulkUpdatePayment(request.getIds(), request.getPaymentStatus());
        return ResponseEntity.ok(ApiResponse.ok());
    }

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
