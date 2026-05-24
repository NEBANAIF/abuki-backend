package com.abuki.service;

import com.abuki.model.StockHistory;
import com.abuki.repository.StockHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StockHistoryService {

    @Autowired private StockHistoryRepository historyRepo;

    // ── READ ALL ─────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<StockHistory> getAll() {
        return historyRepo.findAllOrderedByDate();
    }

    // ── READ BY PRODUCT ───────────────────────────────────
    @Transactional(readOnly = true)
    public List<StockHistory> getByProduct(Long productId) {
        return historyRepo.findByProductId(productId);
    }

    // ── DELETE ───────────────────────────────────────────
    @Transactional
    public void delete(Long id) {
        if (!historyRepo.existsById(id)) {
            throw new RuntimeException("Stock history record not found: " + id);
        }
        historyRepo.deleteById(id);
        historyRepo.flush();
    }
}