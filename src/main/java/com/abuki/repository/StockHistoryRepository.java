package com.abuki.repository;

import com.abuki.model.StockHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockHistoryRepository extends JpaRepository<StockHistory, Long> {

    List<StockHistory> findByProductId(Long productId);

    @Query("SELECT s FROM StockHistory s ORDER BY s.date DESC, s.time DESC")
    List<StockHistory> findAllOrderedByDate();

    @Modifying
    @Query("DELETE FROM StockHistory s WHERE s.product.id = :productId")
    int deleteByProductId(Long productId);
}