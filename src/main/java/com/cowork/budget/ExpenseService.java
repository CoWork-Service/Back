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
    private final ExpensePhotoLinkRepository expensePhotoLinkRepository;
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

        Map<String, Long> byCategory = all.stream()
                .collect(Collectors.groupingBy(
                        Expense::getCategory,
                        Collectors.summingLong(Expense::getAmount)));

        return Map.of(
                "total", total,
                "count", all.size(),
                "byDepartment", byDept,
                "byCategory", byCategory
        );
    }

    @Transactional
    public Expense createExpense(Long cohortId, LocalDate date, Department department, String category,
                                 String vendor, String description, Long amount, String paymentMethod,
                                 String note, Long eventId, MultipartFile receipt, List<Long> photoIds) {
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
        Expense saved = expenseRepository.save(expense);
        replacePhotoLinks(saved.getId(), photoIds);
        return saved;
    }

    @Transactional
    public Expense createExpenseWithReceiptPath(Long cohortId, LocalDate date, Department department, String category,
                                                String vendor, String description, Long amount, String paymentMethod,
                                                String note, Long eventId, String receiptPath, List<Long> photoIds) {
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
        Expense saved = expenseRepository.save(expense);
        replacePhotoLinks(saved.getId(), photoIds);
        return saved;
    }

    @Transactional
    public Expense updateExpense(Long id, LocalDate date, Department department, String category,
                                 String vendor, String description, Long amount, String paymentMethod,
                                 String note, Long eventId, MultipartFile receipt, List<Long> photoIds) {
        Expense expense = findById(id);

        if (receipt != null && !receipt.isEmpty()) {
            if (expense.getReceiptStoragePath() != null) {
                storageService.delete(expense.getReceiptStoragePath());
            }
            String receiptPath = storageService.store(receipt, "expenses", expense.getCohortId());
            expense.setReceiptPath(receiptPath);
        }

        expense.update(
                date != null ? date : expense.getDate(),
                department != null ? department : expense.getDepartment(),
                category != null ? category : expense.getCategory(),
                vendor != null ? vendor : expense.getVendor(),
                description != null ? description : expense.getDescription(),
                amount != null ? amount : expense.getAmount(),
                paymentMethod != null ? paymentMethod : expense.getPaymentMethod(),
                note != null ? note : expense.getNote(),
                eventId != null ? eventId : expense.getEventId()
        );
        if (photoIds != null) {
            replacePhotoLinks(expense.getId(), photoIds);
        }
        return expense;
    }

    @Transactional
    public void deleteExpense(Long id) {
        Expense expense = findById(id);
        if (expense.getReceiptStoragePath() != null) {
            storageService.delete(expense.getReceiptStoragePath());
        }
        expense.softDelete();
        expensePhotoLinkRepository.deleteByExpenseId(id);
    }

    public Expense getExpense(Long id) {
        return findById(id);
    }

    public List<Long> getPhotoIds(Long expenseId) {
        return expensePhotoLinkRepository.findByExpenseId(expenseId).stream()
                .map(ExpensePhotoLink::getPhotoId)
                .toList();
    }

    public Map<Long, List<Long>> getPhotoIdsByExpenseIds(List<Long> expenseIds) {
        if (expenseIds == null || expenseIds.isEmpty()) {
            return Map.of();
        }
        return expensePhotoLinkRepository.findByExpenseIdIn(expenseIds).stream()
                .collect(Collectors.groupingBy(
                        ExpensePhotoLink::getExpenseId,
                        Collectors.mapping(ExpensePhotoLink::getPhotoId, Collectors.toList())
                ));
    }

    private Expense findById(Long id) {
        return expenseRepository.findById(id)
            .filter(e -> !e.isDeleted())
            .orElseThrow(() -> new BusinessException(ErrorCode.EXPENSE_NOT_FOUND));
    }

    private void replacePhotoLinks(Long expenseId, List<Long> photoIds) {
        expensePhotoLinkRepository.deleteByExpenseId(expenseId);
        if (photoIds == null || photoIds.isEmpty()) {
            return;
        }

        photoIds.stream()
                .filter(id -> id != null)
                .distinct()
                .map(photoId -> ExpensePhotoLink.builder()
                        .expenseId(expenseId)
                        .photoId(photoId)
                        .build())
                .forEach(expensePhotoLinkRepository::save);
    }
}
