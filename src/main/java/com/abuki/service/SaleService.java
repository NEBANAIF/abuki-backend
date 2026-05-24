package com.abuki.service;

import com.abuki.model.Product;
import com.abuki.model.Sale;
import com.abuki.audit.AuditService;
import com.abuki.repository.ProductRepository;
import com.abuki.repository.SaleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SaleService {

    @Autowired private SaleRepository    saleRepo;
    @Autowired private ProductRepository productRepo;
    @Autowired private ProductService    productService;
    @Autowired private AuditService      auditService;

    // ── READ ALL ─────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<Sale> getAll() {
        return saleRepo.findAllOrderedByDate();
    }

    // ── READ ONE ─────────────────────────────────────────
    @Transactional(readOnly = true)
    public Sale getById(Long id) {
        return saleRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Sale not found: " + id));
    }

    // ── CREATE (record sale) ─────────────────────────────
    @Transactional
    public Sale recordSale(Sale req) {
        Product product = productRepo.findByIdForUpdate(req.getProduct().getId())
            .orElseThrow(() -> new RuntimeException(
                "Product not found: " + req.getProduct().getId()));

        // Validate stock
        if (req.getQuantity() == null || req.getQuantity() <= 0) {
            throw new RuntimeException("Quantity must be greater than zero.");
        }
        if (product.getStock() < req.getQuantity()) {
            throw new RuntimeException(
                "Insufficient stock for '" + product.getName() +
                "'. Available: " + product.getStock() +
                ", Requested: " + req.getQuantity());
        }

        // Use product price if not provided
        double unitPrice = (req.getPrice() != null && req.getPrice() > 0)
            ? req.getPrice()
            : product.getPrice();

        req.setPrice(unitPrice);
        req.setTotal(unitPrice * req.getQuantity());
        req.setProduct(product);

        if (req.getRecordedBy() == null || req.getRecordedBy().isBlank()) {
            req.setRecordedBy("Admin");
        }

        // Save sale — flush immediately so it appears in DB
        Sale saved = saleRepo.saveAndFlush(req);

        // Deduct stock
        int before = product.getStock();
        int after  = before - req.getQuantity();
        product.setStock(after);
        product.computeStatus();
        productRepo.saveAndFlush(product);

        // Log stock change
        productService.saveHistory(
            product, -req.getQuantity(), before, after,
            "SALE",
            "Sale to " + (req.getCustomerName() != null ? req.getCustomerName() : "customer"),
            req.getRecordedBy(),
            "SALE-" + saved.getId()
        );

        auditService.record("SALE_RECORDED", "Sale", String.valueOf(saved.getId()),
            "product=" + product.getId() + " qty=" + req.getQuantity() + " total=" + saved.getTotal());

        return saved;
    }

    // ── DELETE (restores stock) ───────────────────────────
    @Transactional
    public void deleteSale(Long id) {
        // Load sale fresh from DB
        Sale sale = saleRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Sale not found: " + id));

        Product product = productRepo.findByIdForUpdate(sale.getProduct().getId())
            .orElseThrow(() -> new RuntimeException("Product not found: " + sale.getProduct().getId()));

        // Restore stock
        int before = product.getStock();
        int after  = before + sale.getQuantity();
        product.setStock(after);
        product.computeStatus();
        productRepo.saveAndFlush(product);

        // Log stock restoration
        productService.saveHistory(
            product, sale.getQuantity(), before, after,
            "ADJUSTMENT",
            "Stock restored — sale #" + id + " deleted",
            "System",
            "SALE-DEL-" + id
        );

        // Hard delete sale — flush immediately
        saleRepo.deleteById(id);
        saleRepo.flush();

        auditService.record("SALE_DELETED", "Sale", String.valueOf(id),
            "product=" + product.getId() + " restoredQty=" + sale.getQuantity());
    }
}