package com.cowork.budget;

import com.cowork.cohort.Department;
import com.cowork.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ExpenseResponse>>> getExpenses(
            @RequestParam Long cohortId,
            @RequestParam(required = false) Department department,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        List<ExpenseResponse> list = expenseService.getExpenses(cohortId, department, category, dateFrom, dateTo)
                .stream().map(ExpenseResponse::of).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary(@RequestParam Long cohortId) {
        return ResponseEntity.ok(ApiResponse.ok(expenseService.getSummary(cohortId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ExpenseResponse>> createExpense(
            @RequestParam LocalDate date,
            @RequestParam Long cohortId,
            @RequestParam Department department,
            @RequestParam String category,
            @RequestParam String vendor,
            @RequestParam(required = false) String description,
            @RequestParam Long amount,
            @RequestParam String paymentMethod,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) MultipartFile receipt) {
        Expense expense = expenseService.createExpense(cohortId, date, department, category, vendor,
                description, amount, paymentMethod, note, eventId, receipt);
        return ResponseEntity.ok(ApiResponse.ok(ExpenseResponse.of(expense)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExpenseResponse>> getExpense(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(ExpenseResponse.of(expenseService.getExpense(id))));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ExpenseResponse>> updateExpense(
            @PathVariable Long id,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) Department department,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Long amount,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) MultipartFile receipt) {
        Expense expense = expenseService.updateExpense(id, date, department, category, vendor,
                description, amount, paymentMethod, note, eventId, receipt);
        return ResponseEntity.ok(ApiResponse.ok(ExpenseResponse.of(expense)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteExpense(@PathVariable Long id) {
        expenseService.deleteExpense(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    record ExpenseResponse(Long id, Long cohortId, LocalDate date, String department, String category,
                           String vendor, String description, Long amount, String paymentMethod,
                           String receiptStoragePath, String note, Long eventId,
                           LocalDateTime createdAt, LocalDateTime updatedAt) {
        static ExpenseResponse of(Expense e) {
            return new ExpenseResponse(e.getId(), e.getCohortId(), e.getDate(),
                    e.getDepartment().name(), e.getCategory(), e.getVendor(), e.getDescription(),
                    e.getAmount(), e.getPaymentMethod(), e.getReceiptStoragePath(), e.getNote(),
                    e.getEventId(), e.getCreatedAt(), e.getUpdatedAt());
        }
    }
}
