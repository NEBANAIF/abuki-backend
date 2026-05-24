package com.abuki.repository;

import com.abuki.model.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SaleRepository extends JpaRepository<Sale, Long> {

    // ── Existing queries ──────────────────────────────────────────────────────

    List<Sale> findByProductId(Long productId);

    @Query("SELECT s FROM Sale s ORDER BY s.saleDate DESC, s.saleTime DESC")
    List<Sale> findAllOrderedByDate();

    @Modifying
    @Query("DELETE FROM Sale s WHERE s.product.id = :productId")
    int deleteByProductId(Long productId);

    // ── Analytics: totals over a date range ──────────────────────────────────

    /**
     * Total revenue (sum of sale.total) between two dates inclusive.
     */
    @Query("SELECT SUM(s.total) FROM Sale s WHERE s.saleDate BETWEEN :from AND :to")
    Double sumTotalBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Total units sold between two dates inclusive.
     */
    @Query("SELECT SUM(s.quantity) FROM Sale s WHERE s.saleDate BETWEEN :from AND :to")
    Long sumQuantityBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Number of sale rows between two dates inclusive.
     */
    @Query("SELECT COUNT(s) FROM Sale s WHERE s.saleDate BETWEEN :from AND :to")
    Long countSalesBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Total COGS (cost × quantity) between two dates inclusive.
     * Requires Sale → Product join; product.cost must be non-null.
     */
    @Query("SELECT SUM(s.product.cost * s.quantity) FROM Sale s " +
           "WHERE s.saleDate BETWEEN :from AND :to AND s.product.cost IS NOT NULL")
    Double sumCogsBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // ── Analytics: daily series ───────────────────────────────────────────────

    /**
     * Returns [saleDate, SUM(total), SUM(quantity)] grouped by day.
     * Used to build the daily revenue/qty time-series chart.
     */
    @Query("SELECT s.saleDate, SUM(s.total), SUM(s.quantity) " +
           "FROM Sale s " +
           "WHERE s.saleDate BETWEEN :from AND :to " +
           "GROUP BY s.saleDate " +
           "ORDER BY s.saleDate")
    List<Object[]> sumBySaleDate(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Returns [saleDate, SUM(cost * quantity)] grouped by day.
     * Used to compute daily COGS for the profit/loss series.
     */
    @Query("SELECT s.saleDate, SUM(s.product.cost * s.quantity) " +
           "FROM Sale s " +
           "WHERE s.saleDate BETWEEN :from AND :to AND s.product.cost IS NOT NULL " +
           "GROUP BY s.saleDate " +
           "ORDER BY s.saleDate")
    List<Object[]> sumCogsBySaleDate(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // ── Analytics: monthly series ─────────────────────────────────────────────

    /**
     * Returns [year, month, SUM(total), SUM(quantity)] grouped by year+month.
     * Used to build the monthly revenue/qty time-series chart.
     */
    @Query("SELECT YEAR(s.saleDate), MONTH(s.saleDate), SUM(s.total), SUM(s.quantity) " +
           "FROM Sale s " +
           "WHERE s.saleDate BETWEEN :from AND :to " +
           "GROUP BY YEAR(s.saleDate), MONTH(s.saleDate) " +
           "ORDER BY YEAR(s.saleDate), MONTH(s.saleDate)")
    List<Object[]> sumByYearMonth(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Returns [year, month, SUM(cost * quantity)] grouped by year+month.
     * Used to compute monthly COGS for the profit/loss series.
     */
    @Query("SELECT YEAR(s.saleDate), MONTH(s.saleDate), SUM(s.product.cost * s.quantity) " +
           "FROM Sale s " +
           "WHERE s.saleDate BETWEEN :from AND :to AND s.product.cost IS NOT NULL " +
           "GROUP BY YEAR(s.saleDate), MONTH(s.saleDate) " +
           "ORDER BY YEAR(s.saleDate), MONTH(s.saleDate)")
    List<Object[]> sumCogsByYearMonth(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // ── Analytics: hourly series ──────────────────────────────────────────────

    /**
     * Returns all Sale entities for a single day (used to compute the hourly series).
     * Spring Data derives this from the method name automatically — no @Query needed.
     */
    List<Sale> findBySaleDateBetween(LocalDate from, LocalDate to);

    // ── Analytics: product revenue ranking ───────────────────────────────────

    /**
     * Returns [product.name, SUM(total), SUM(quantity)] per product in the
     * date range, ordered by revenue descending.
     * Used to build the Top N / Bottom N product ranking on the dashboard.
     */
    @Query("SELECT s.product.name, SUM(s.total), SUM(s.quantity) " +
           "FROM Sale s " +
           "WHERE s.saleDate BETWEEN :from AND :to " +
           "GROUP BY s.product.id, s.product.name " +
           "ORDER BY SUM(s.total) DESC")
    List<Object[]> findProductRevenueBetween(
            @Param("from") LocalDate from,
            @Param("to")   LocalDate to);
}