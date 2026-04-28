package com.cowork.memo;

import com.cowork.cohort.Department;
import com.cowork.common.BusinessException;
import com.cowork.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemoService {

    private final MemoRepository memoRepository;

    public List<Memo> getMemos(Long cohortId, MemoStatus status, MemoPriority priority, Department department) {
        return memoRepository.findFiltered(cohortId, status, priority, department);
    }

    @Transactional
    public Memo createMemo(Long cohortId, String title, String content, Department department,
                           MemoPriority priority, MemoStatus status, LocalDate dueDate, String author) {
        Memo memo = Memo.builder()
                .cohortId(cohortId)
                .title(title)
                .content(content)
                .department(department)
                .priority(priority != null ? priority : MemoPriority.NORMAL)
                .status(status != null ? status : MemoStatus.OPEN)
                .dueDate(dueDate)
                .author(author)
                .build();
        return memoRepository.save(memo);
    }

    @Transactional
    public Memo updateMemo(Long id, String title, String content, Department department,
                           MemoPriority priority, MemoStatus status, LocalDate dueDate) {
        Memo memo = findById(id);
        memo.update(title, content, department, priority, status, dueDate);
        return memo;
    }

    @Transactional
    public Memo updateStatus(Long id, MemoStatus status) {
        Memo memo = findById(id);
        memo.updateStatus(status);
        return memo;
    }

    @Transactional
    public Memo updatePriority(Long id, MemoPriority priority) {
        Memo memo = findById(id);
        memo.updatePriority(priority);
        return memo;
    }

    @Transactional
    public void deleteMemo(Long id) {
        Memo memo = findById(id);
        memo.softDelete();
    }

    private Memo findById(Long id) {
        return memoRepository.findById(id)
                .filter(m -> !m.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMO_NOT_FOUND));
    }
}
