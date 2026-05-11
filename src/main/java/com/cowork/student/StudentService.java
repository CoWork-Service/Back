package com.cowork.student;

import com.cowork.common.BusinessException;
import com.cowork.common.ErrorCode;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;

    public List<Student> getStudents(Long cohortId, Integer grade, PaymentStatus paymentStatus, String search) {
        String keyword = StringUtils.hasText(search) ? search.trim() : null;
        return studentRepository.findFiltered(cohortId, grade, paymentStatus, keyword);
    }

    public Map<String, Object> getSummary(Long cohortId) {
        List<Student> students = studentRepository.findByCohortIdAndDeletedAtIsNullOrderByStudentNumberAsc(cohortId);
        long paid = students.stream().filter(student -> student.getPaymentStatus() == PaymentStatus.PAID).count();
        long total = students.size();
        long unpaid = total - paid;
        long paymentRate = total == 0 ? 0 : Math.round((double) paid * 100 / total);

        return Map.of(
                "total", total,
                "paid", paid,
                "unpaid", unpaid,
                "paymentRate", paymentRate
        );
    }

    public Student getStudent(Long id) {
        return findById(id);
    }

    @Transactional
    public Student createStudent(Long cohortId, String studentNumber, String name, String department,
                                 Integer grade, PaymentStatus paymentStatus, LocalDateTime paidAt, String note) {
        if (studentRepository.existsByCohortIdAndStudentNumberAndDeletedAtIsNull(cohortId, studentNumber)) {
            throw new BusinessException(ErrorCode.STUDENT_DUPLICATE);
        }

        Student student = Student.builder()
                .cohortId(cohortId)
                .studentNumber(studentNumber)
                .name(name)
                .department(department)
                .grade(grade)
                .paymentStatus(paymentStatus != null ? paymentStatus : PaymentStatus.UNPAID)
                .paidAt(paymentStatus == PaymentStatus.PAID ? (paidAt != null ? paidAt : LocalDateTime.now()) : null)
                .note(note)
                .build();
        return studentRepository.save(student);
    }

    @Transactional
    public Student updateStudent(Long id, String studentNumber, String name, String department,
                                 Integer grade, PaymentStatus paymentStatus, LocalDateTime paidAt, String note) {
        Student student = findById(id);

        if (StringUtils.hasText(studentNumber) && !studentNumber.equals(student.getStudentNumber())) {
            studentRepository.findByCohortIdAndStudentNumberAndDeletedAtIsNull(student.getCohortId(), studentNumber)
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new BusinessException(ErrorCode.STUDENT_DUPLICATE);
                    });
        }

        student.update(studentNumber, name, department, grade, note);
        if (paymentStatus != null) {
            student.applyPaymentStatus(paymentStatus, paidAt);
        }
        return student;
    }

    @Transactional
    public void deleteStudent(Long id) {
        findById(id).softDelete();
    }

    @Transactional
    public void bulkUpdatePayment(List<Long> ids, PaymentStatus paymentStatus) {
        List<Student> students = studentRepository.findAllById(ids);
        for (Student student : students) {
            if (student.isDeleted()) {
                continue;
            }
            student.applyPaymentStatus(paymentStatus, null);
        }
    }

    @Transactional
    public StudentImportResult importStudents(Long cohortId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        int created = 0;
        int updated = 0;
        List<Student> processed = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String[] headerRow = reader.readNext();
            if (headerRow == null) {
                return new StudentImportResult(0, 0, 0, List.of());
            }

            Map<String, Integer> headers = buildHeaderIndex(headerRow);
            String[] row;
            while ((row = reader.readNext()) != null) {
                String studentNumber = getValue(row, headers, "studentNumber");
                String name = getValue(row, headers, "name");
                if (!StringUtils.hasText(studentNumber) || !StringUtils.hasText(name)) {
                    continue;
                }

                String department = getValue(row, headers, "department");
                Integer grade = parseInteger(getValue(row, headers, "grade"));
                PaymentStatus paymentStatus = parsePaymentStatus(getValue(row, headers, "paymentStatus"));
                LocalDateTime paidAt = parseDateTime(getValue(row, headers, "paidAt"));
                String note = getValue(row, headers, "note");

                Student student = studentRepository.findByCohortIdAndStudentNumberAndDeletedAtIsNull(cohortId, studentNumber)
                        .orElse(null);
                if (student == null) {
                    student = Student.builder()
                            .cohortId(cohortId)
                            .studentNumber(studentNumber)
                            .name(name)
                            .department(department)
                            .grade(grade)
                            .paymentStatus(paymentStatus)
                            .paidAt(paymentStatus == PaymentStatus.PAID ? paidAt : null)
                            .note(note)
                            .build();
                    studentRepository.save(student);
                    created++;
                } else {
                    student.update(studentNumber, name, department, grade, note);
                    student.applyPaymentStatus(paymentStatus, paidAt);
                    updated++;
                }
                processed.add(student);
            }
        } catch (IOException | CsvValidationException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        return new StudentImportResult(processed.size(), created, updated, processed);
    }

    private Student findById(Long id) {
        return studentRepository.findById(id)
                .filter(student -> !student.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.STUDENT_NOT_FOUND));
    }

    private Map<String, Integer> buildHeaderIndex(String[] headerRow) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < headerRow.length; i++) {
            String normalized = normalizeHeader(headerRow[i]);
            if (matches(normalized, "studentnumber", "studentid", "학번")) {
                index.put("studentNumber", i);
            } else if (matches(normalized, "name", "이름")) {
                index.put("name", i);
            } else if (matches(normalized, "department", "major", "학과", "학부", "전공")) {
                index.put("department", i);
            } else if (matches(normalized, "grade", "학년")) {
                index.put("grade", i);
            } else if (matches(normalized, "paymentstatus", "납부상태", "status")) {
                index.put("paymentStatus", i);
            } else if (matches(normalized, "paidat", "paymentdate", "납부일")) {
                index.put("paidAt", i);
            } else if (matches(normalized, "note", "memo", "비고")) {
                index.put("note", i);
            }
        }
        return index;
    }

    private String getValue(String[] row, Map<String, Integer> headers, String key) {
        Integer idx = headers.get(key);
        if (idx == null || idx >= row.length) {
            return null;
        }
        String value = row[idx];
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeHeader(String value) {
        return value == null
                ? ""
                : value.replace(" ", "")
                .replace("_", "")
                .replace("-", "")
                .toLowerCase(Locale.ROOT);
    }

    private boolean matches(String value, String... candidates) {
        for (String candidate : candidates) {
            if (candidate.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private Integer parseInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private PaymentStatus parsePaymentStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return PaymentStatus.UNPAID;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("PAID") || normalized.equals("납부") || normalized.equals("완료") || normalized.equals("Y")) {
            return PaymentStatus.PAID;
        }
        return PaymentStatus.UNPAID;
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
        );

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(value.trim(), formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        try {
            return LocalDate.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    public record StudentImportResult(int total, int created, int updated, List<Student> students) {
    }
}
