package com.abuki.repository;

import com.abuki.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    // All expenses newest first
    @Query("SELECT e FROM Expense e ORDER BY e.date DESC, e.createdAt DESC")
    List<Expense> findAllOrdered();

    // By date range
    List<Expense> findByDateBetween(LocalDate from, LocalDate to);

    // By category
    List<Expense> findByCategory(String category);

    // Total expenses between dates
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.date BETWEEN :from AND :to")
    Double sumBetween(LocalDate from, LocalDate to);

    @Query("SELECT e.date, COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.date BETWEEN :from AND :to GROUP BY e.date ORDER BY e.date")
    List<Object[]> sumByExpenseDate(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT YEAR(e.date), MONTH(e.date), COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.date BETWEEN :from AND :to GROUP BY YEAR(e.date), MONTH(e.date) ORDER BY YEAR(e.date), MONTH(e.date)")
    List<Object[]> sumExpenseByYearMonth(@Param("from") LocalDate from, @Param("to") LocalDate to);
}