package com.abuki.service;

import com.abuki.model.Expense;
import com.abuki.repository.ExpenseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ExpenseService {

    @Autowired
    private ExpenseRepository expenseRepo;

    // ── READ ALL ─────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<Expense> getAll() {
        return expenseRepo.findAllOrdered();
    }

    // ── READ ONE ─────────────────────────────────────────
    @Transactional(readOnly = true)
    public Expense getById(Long id) {
        return expenseRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Expense not found: " + id));
    }

    // ── CREATE ───────────────────────────────────────────
    @Transactional
    public Expense create(Expense req) {
        if (req.getCategory() == null || req.getCategory().isBlank()) {
            throw new RuntimeException("Category is required.");
        }
        if (req.getAmount() == null || req.getAmount() <= 0) {
            throw new RuntimeException("Amount must be greater than zero.");
        }
        return expenseRepo.saveAndFlush(req);
    }

    // ── UPDATE ───────────────────────────────────────────
    @Transactional
    public Expense update(Long id, Expense req) {
        Expense existing = getById(id);
        existing.setCategory(req.getCategory());
        existing.setAmount(req.getAmount());
        existing.setDescription(req.getDescription());
        existing.setDate(req.getDate());
        return expenseRepo.saveAndFlush(existing);
    }

    // ── DELETE ───────────────────────────────────────────
    @Transactional
    public void delete(Long id) {
        if (!expenseRepo.existsById(id)) {
            throw new RuntimeException("Expense not found: " + id);
        }
        expenseRepo.deleteById(id);
        expenseRepo.flush();
    }
}