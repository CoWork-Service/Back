package com.cowork.mobile;

import com.cowork.budget.Expense;
import com.cowork.budget.ExpenseService;
import com.cowork.cohort.Department;
import com.cowork.common.BusinessException;
import com.cowork.common.ErrorCode;
import com.cowork.common.storage.FileStorageService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MobileSessionService {

    private final MobileSessionRepository mobileSessionRepository;
    private final ExpenseService expenseService;
    private final FileStorageService storageService;
    private final ObjectMapper objectMapper;

    @Transactional
    public MobileSession createSession(Long cohortId, Long createdBy) {
        MobileSession session = MobileSession.builder()
                .sessionToken(UUID.randomUUID().toString().replace("-", ""))
                .cohortId(cohortId)
                .createdBy(createdBy)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
        return mobileSessionRepository.save(session);
    }

    public MobileSession getSession(String token) {
        return mobileSessionRepository.findBySessionToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.MOBILE_SESSION_NOT_FOUND));
    }

    @Transactional
    public MobileSession upload(String token, MultipartFile photo, String extraDataJson) {
        MobileSession session = validateUsable(token);
        String photoPath = storageService.store(photo, "expenses", session.getCohortId());
        session.complete(photoPath, parseExtraData(extraDataJson));
        return session;
    }

    public MobileSession getResult(String token) {
        return getSession(token);
    }

    @Transactional
    public Expense createExpense(String token, LocalDate date, Department department, String category,
                                 String vendor, String description, Long amount, String paymentMethod,
                                 String note, Long eventId, List<Long> photoIds) {
        MobileSession session = getSession(token);
        if (session.isExpired()) {
            throw new BusinessException(ErrorCode.MOBILE_SESSION_EXPIRED);
        }
        if (!session.isUsed() || session.getPhotoPath() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (session.getExpenseId() != null) {
            return expenseService.getExpense(session.getExpenseId());
        }

        Expense expense = expenseService.createExpenseWithReceiptPath(
                session.getCohortId(), date, department, category, vendor, description,
                amount, paymentMethod, note, eventId, session.getPhotoPath(), photoIds
        );
        session.attachExpense(expense.getId());
        return expense;
    }

    @Transactional
    public void deleteSession(String token) {
        MobileSession session = getSession(token);
        if (session.getPhotoPath() != null && session.getExpenseId() == null) {
            storageService.delete(session.getPhotoPath());
        }
        mobileSessionRepository.delete(session);
    }

    private MobileSession validateUsable(String token) {
        MobileSession session = getSession(token);
        if (session.isExpired()) {
            throw new BusinessException(ErrorCode.MOBILE_SESSION_EXPIRED);
        }
        if (session.isUsed()) {
            throw new BusinessException(ErrorCode.MOBILE_SESSION_USED);
        }
        return session;
    }

    private Map<String, Object> parseExtraData(String extraDataJson) {
        if (!StringUtils.hasText(extraDataJson)) {
            return null;
        }
        try {
            return objectMapper.readValue(extraDataJson, new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
