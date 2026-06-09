package com.abuki.service;

import com.abuki.model.Product;
import com.abuki.model.Sale;
import com.abuki.repository.SaleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Sale service — read operations cached, write operations evict
 * both "products" and "analytics" caches.
 *
 * Why "analytics" is also evicted on sale writes:
 *  Analytics KPIs (revenue, COGS, counts) are derived from sales data.
 *  When a sale is recorded or deleted, the cached analytics result is stale
 *  and must be cleared so the next analytics request recomputes from Neon.
 */
@Service
@Transactional
public class SaleService {

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private ProductService productService;

    // ── Read: cached ─────────────────────────────────────

    /**
     * Returns all sales ordered newest first.
     * Cached under key "allSales" — evicted on any write.
     */
    @Cacheable(value = "products", key = "'allSales'")
    @Transactional(readOnly = true)
    public List<Sale> getAll() {
        return saleRepository.findAllOrderedByDate();
    }

    /**
     * Returns a single sale by ID.
     * Cached per sale ID.
     */
    @Cacheable(value = "products", key = "'sale:' + #id")
    @Transactional(readOnly = true)
    public Sale getById(Long id) {
        return saleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sale not found with id: " + id));
    }

    // ── Write: cache-evicting ─────────────────────────────

    /**
     * Records a new sale:
     *  1. Validates product stock is sufficient
     *  2. Calculates total = price × quantity
     *  3. Saves the sale row
     *  4. Deducts stock from the product and logs stock history
     *
     * Evicts "products" cache (stock changed) and "analytics" cache (revenue changed).
     */
    @Caching(evict = {
        @CacheEvict(value = "products",  allEntries = true),
        @CacheEvict(value = "analytics", allEntries = true)
    })
    public Sale recordSale(Sale sale) {
        Product product = productService.getById(sale.getProduct().getId());

        // Validate sufficient stock before recording the sale
        if (product.getStock() < sale.getQuantity()) {
            throw new RuntimeException(
                "Insufficient stock for '" + product.getName() +
                "'. Available: " + product.getStock() +
                ", Requested: " + sale.getQuantity());
        }

        // Use product price if sale price not supplied by frontend
        if (sale.getPrice() == null) {
            sale.setPrice(product.getPrice());
        }

        // Calculate and set total revenue for this sale
        sale.setTotal(sale.getPrice() * sale.getQuantity());
        sale.setProduct(product);

        // Persist the sale row
        Sale saved = saleRepository.save(sale);

        // Deduct stock and record the change in stock history
        int prevStock = product.getStock();
        int newStock  = prevStock - sale.getQuantity();
        product.setStock(newStock);

        productService.recordHistory(
            product,
            -sale.getQuantity(),
            prevStock, newStock,
            "SALE",
            "Sale to " + (sale.getCustomerName() != null ? sale.getCustomerName() : "customer"),
            sale.getRecordedBy() != null ? sale.getRecordedBy() : "Admin",
            "SALE-" + saved.getId()
        );

        // Save updated stock — triggers @PreUpdate on Product which recalculates status
        productService.update(product.getId(), product);

        return saved;
    }

    /**
     * Deletes a sale and restores stock to the product.
     *
     * Evicts "products" cache (stock restored) and "analytics" cache (revenue changed).
     */
    @Caching(evict = {
        @CacheEvict(value = "products",  allEntries = true),
        @CacheEvict(value = "analytics", allEntries = true)
    })
    public void deleteSale(Long id) {
        Sale sale    = saleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sale not found with id: " + id));
        Product product = sale.getProduct();

        // Restore stock to the product
        int prevStock = product.getStock();
        int newStock  = prevStock + sale.getQuantity();
        product.setStock(newStock);

        productService.recordHistory(
            product,
            sale.getQuantity(),
            prevStock, newStock,
            "ADJUSTMENT",
            "Stock restored — sale #" + id + " deleted",
            "System",
            "SALE-DEL-" + id
        );

        productService.update(product.getId(), product);
        saleRepository.delete(sale);
    }
}
