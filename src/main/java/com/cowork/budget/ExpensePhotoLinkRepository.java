package com.cowork.budget;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpensePhotoLinkRepository extends JpaRepository<ExpensePhotoLink, Long> {

    List<ExpensePhotoLink> findByExpenseId(Long expenseId);

    List<ExpensePhotoLink> findByExpenseIdIn(List<Long> expenseIds);

    void deleteByExpenseId(Long expenseId);
}
