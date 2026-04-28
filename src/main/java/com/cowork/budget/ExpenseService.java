package com.cowork.budget;

import com.cowork.cohort.Department;
import com.cowork.common.BusinessException;
import com.cowork.common.ErrorCode;
import com.cowork.common.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final FileStorageService storageService;

    public List<Expense> getExpenses(Long cohortId, Department department, String category,
                                     LocalDate dateFrom, LocalDate dateTo) {
        return expenseRepository.findFiltered(cohortId, department, category, dateFrom, dateTo);
    }

    public Map<String, Object> getSummary(Long cohortId) {
        List<Expense> all = expenseRepository.findByCohortIdAndDeletedAtIsNullOrderByDateDesc(cohortId);
        long total = all.stream().mapToLong(Expense::getAmount).sum();

        Map<String, Long> byDept = all.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getDepartment().name(),
                        Collectors.summingLong(Expense::getAmount)));

        return Map.of("total", total, "count", all.size(), "byDepartment", byDept);
    }

    @Transactional
    public Expense createExpense(Long cohortId, LocalDate date, Department department, String category,
                                 String vendor, String description, Long amount, String paymentMethod,
                                 String note, Long eventId, MultipartFile receipt) {
        String receiptPath = null;
        if (receipt != null && !receipt.isEmpty()) {
            receiptPath = storageService.store(receipt, "expenses", cohortId);
        }

        Expense expense = Expense.builder()
                .cohortId(cohortId)
                .date(date)
                .department(department)
                .category(category)
                .vendor(vendor)
                .description(description)
                .amount(amount)
                .paymentMethod(paymentMethod)
                .receiptStoragePath(receiptPath)
                .note(note)
                .eventId(eventId)
                .build();
        return expenseRepository.save(expense);
    }

    @Transactional
    public Expense updateExpense(Long id, LocalDate date, Department department, String category,
                                 String vendor, String description, Long amount, String paymentMethod,
                                 String note, Long eventId, MultipartFile receipt) {
        Expense expense = findById(id);

        if (receipt != null && !receipt.isEmpty()) {
            if (expense.getReceiptStoragePath() != null) {
                storageService.delete(expense.getReceiptStoragePath());
            }
            String receiptPath = storageService.store(receipt, "expenses", expense.getCohortId());
            expense.setReceiptPath(receiptPath);
        }

        expense.update(date, department, category, vendor, description, amount, paymentMethod, note, eventId);
        return expense;
    }

    @Transactional
    public void deleteExpense(Long id) {
        Expense expense = findById(id);
        if (expense.getReceiptStoragePath() != null) {
            storageService.delete(expense.getReceiptStoragePath());
        }
        expense.softDelete();
    }

    public Expense getExpense(Long id) {
        return findById(id);
    }

    private Expense findById(Long id) {
        return expenseRepository.findById(id)
                .filter(e -> !e.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.EXPENSE_NOT_FOUND));
    }
}
