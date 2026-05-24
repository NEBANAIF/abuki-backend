package com.abuki.service;

import com.abuki.model.Product;
import com.abuki.model.StockHistory;
import com.abuki.repository.ProductRepository;
import com.abuki.repository.SaleRepository;
import com.abuki.repository.StockHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    @Autowired private ProductRepository     productRepo;
    @Autowired private SaleRepository        saleRepo;
    @Autowired private StockHistoryRepository historyRepo;

    // ── READ ALL ─────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<Product> getAll() {
        return productRepo.findAll();
    }

    // ── READ ONE ─────────────────────────────────────────
    @Transactional(readOnly = true)
    public Product getById(Long id) {
        return productRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Product not found: " + id));
    }

    // ── SEARCH ───────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<Product> search(String keyword) {
        return productRepo.search(keyword);
    }

    // ── LOW / OUT STOCK ───────────────────────────────────
    @Transactional(readOnly = true)
    public List<Product> getLowStock() { return productRepo.findLowStock(); }

    @Transactional(readOnly = true)
    public List<Product> getOutOfStock() { return productRepo.findOutOfStock(); }

    // ── CREATE ───────────────────────────────────────────
    @Transactional
    public Product create(Product req) {
        // SKU must be unique
        if (productRepo.findBySku(req.getSku()).isPresent()) {
            throw new RuntimeException("SKU already exists: " + req.getSku());
        }

        // Safe defaults
        if (req.getCost()     == null) req.setCost(0.0);
        if (req.getStock()    == null) req.setStock(0);
        if (req.getMinStock() == null) req.setMinStock(30);
        if (req.getPrice()    == null) req.setPrice(0.0);

        // Compute status before saving
        req.computeStatus();

        // Save and immediately flush to DB
        Product saved = productRepo.saveAndFlush(req);

        // Log initial stock if > 0
        if (saved.getStock() > 0) {
            saveHistory(saved, saved.getStock(), 0, saved.getStock(),
                "STOCK_ADDITION", "Initial stock", "System", "INIT-" + saved.getId());
        }

        return saved;
    }

    // ── UPDATE ───────────────────────────────────────────
    @Transactional
    public Product update(Long id, Product req) {
        Product p = getById(id);

        // Check SKU uniqueness only if changed
        if (!p.getSku().equals(req.getSku())) {
            if (productRepo.findBySku(req.getSku()).isPresent()) {
                throw new RuntimeException("SKU already exists: " + req.getSku());
            }
        }

        // Apply all field updates
        p.setName(req.getName());
        p.setSku(req.getSku());
        p.setPrice(req.getPrice() != null ? req.getPrice() : 0.0);
        p.setCost(req.getCost() != null ? req.getCost() : 0.0);
        p.setMinStock(req.getMinStock() != null ? req.getMinStock() : 30);
        p.setCategory(req.getCategory());
        p.setDescription(req.getDescription());

        // Only update stock if explicitly provided
        if (req.getStock() != null) {
            p.setStock(req.getStock());
        }

        // Recompute status
        p.computeStatus();

        // Save and flush to DB immediately
        return productRepo.saveAndFlush(p);
    }

    // ── ADJUST STOCK (add or subtract) ───────────────────
    @Transactional
    public Product adjustStock(Long id, int change, String reason, String user) {
        Product p = productRepo.findByIdForUpdate(id)
            .orElseThrow(() -> new RuntimeException("Product not found: " + id));

        int before = p.getStock();
        int after  = before + change;

        if (after < 0) {
            throw new RuntimeException(
                "Insufficient stock. Available: " + before + ", requested: " + Math.abs(change));
        }

        p.setStock(after);
        p.computeStatus();
        Product saved = productRepo.saveAndFlush(p);

        String type = change > 0 ? "STOCK_ADDITION" : "ADJUSTMENT";
        saveHistory(saved, change, before, after, type, reason, user, null);

        return saved;
    }

    // ── DELETE ───────────────────────────────────────────
    // Order matters: delete FK children before parent
    @Transactional
    public void delete(Long id) {
        if (!productRepo.existsById(id)) {
            throw new RuntimeException("Product not found: " + id);
        }

        // 1. Delete stock_history rows for this product
        historyRepo.deleteByProductId(id);
        historyRepo.flush();

        // 2. Delete sales rows for this product
        saleRepo.deleteByProductId(id);
        saleRepo.flush();

        // 3. Delete the product
        productRepo.deleteById(id);
        productRepo.flush();
    }

    // ── INTERNAL: save stock history ─────────────────────
    @Transactional
    public void saveHistory(Product product, int change,
                             int before, int after,
                             String type, String reason,
                             String user, String reference) {
        StockHistory h = new StockHistory();
        h.setProduct(product);
        h.setQuantityChange(change);
        h.setPreviousStock(before);
        h.setNewStock(after);
        h.setType(type);
        h.setReason(reason);
        h.setUser(user != null ? user : "Admin");
        h.setReference(reference);
        historyRepo.saveAndFlush(h);
    }
}