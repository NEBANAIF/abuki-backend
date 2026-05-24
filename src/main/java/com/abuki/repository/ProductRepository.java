package com.abuki.repository;

import com.abuki.model.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    List<Product> findByStatus(String status);

    long countByStatus(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT COALESCE(SUM(p.stock * p.cost), 0) FROM Product p WHERE p.stock > 0")
    Double sumInventoryValue();

    /** In-stock units with quantity clearly above minimum threshold (enterprise “high stock”). */
    @Query("SELECT COUNT(p) FROM Product p WHERE p.status = 'IN_STOCK' AND p.stock > (2 * p.minStock)")
    long countHighStockProducts();

    @Query("SELECT p FROM Product p WHERE " +
           "LOWER(p.name) LIKE LOWER(CONCAT('%',:keyword,'%')) OR " +
           "LOWER(p.sku)  LIKE LOWER(CONCAT('%',:keyword,'%')) OR " +
           "LOWER(p.category) LIKE LOWER(CONCAT('%',:keyword,'%'))")
    List<Product> search(String keyword);

    @Query("SELECT p FROM Product p WHERE p.stock > 0 AND p.stock <= p.minStock")
    List<Product> findLowStock();

    @Query("SELECT p FROM Product p WHERE p.stock = 0")
    List<Product> findOutOfStock();
}