package com.abuki.service;

import com.abuki.dto.analytics.AnalyticsDashboardResponse;
import com.abuki.dto.analytics.TimeSeriesPoint;
import com.abuki.dto.analytics.TopProductEntry;
import com.abuki.model.Sale;
import com.abuki.repository.ExpenseRepository;
import com.abuki.repository.ProductRepository;
import com.abuki.repository.SaleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AnalyticsService {

    private static final DateTimeFormatter DAY_LABEL   = DateTimeFormatter.ofPattern("MMM d",  Locale.US);
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yy", Locale.US);

    @Autowired private SaleRepository    saleRepo;
    @Autowired private ExpenseRepository expenseRepo;
    @Autowired private ProductRepository productRepo;

    // ── Public entry points ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AnalyticsDashboardResponse dashboard(LocalDate from, LocalDate to, String granularity) {
        return dashboard(from, to, granularity, true);
    }

    @Transactional(readOnly = true)
    public AnalyticsDashboardResponse dashboard(
            LocalDate from, LocalDate to, String granularity, boolean includeSeries) {

        String g = granularity == null ? "day" : granularity.toLowerCase(Locale.ROOT);
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("'to' must be on or after 'from'.");
        }

        // ── Aggregate totals ──────────────────────────────────────────────────
        double revenue   = nz(saleRepo.sumTotalBetween(from, to));
        long   qty       = nzLong(saleRepo.sumQuantityBetween(from, to));
        long   saleCount = nzLong(saleRepo.countSalesBetween(from, to));
        double cogs      = nz(saleRepo.sumCogsBetween(from, to));
        double expenses  = nz(expenseRepo.sumBetween(from, to));

        double grossProfit  = revenue - cogs;
        double netProfit    = grossProfit - expenses;
        double grossMargin  = revenue > 0 ? (grossProfit / revenue) * 100.0 : 0.0;
        double netMargin    = revenue > 0 ? (netProfit   / revenue) * 100.0 : 0.0;
        double avgOrder     = saleCount > 0 ? revenue / saleCount : 0.0;
        double inventoryValue = nz(productRepo.sumInventoryValue());

        // ── Build response ────────────────────────────────────────────────────
        AnalyticsDashboardResponse res = new AnalyticsDashboardResponse();
        res.setFrom(from.toString());
        res.setTo(to.toString());
        res.setGranularity(g);
        res.setTotalRevenue(round2(revenue));
        res.setTotalQuantity(qty);
        res.setSaleCount(saleCount);
        res.setCogs(round2(cogs));
        res.setExpenses(round2(expenses));
        res.setGrossProfit(round2(grossProfit));
        res.setNetProfit(round2(netProfit));
        res.setGrossMarginPct(round2(grossMargin));
        res.setNetMarginPct(round2(netMargin));
        res.setAvgOrderValue(round2(avgOrder));
        res.setInventoryValue(round2(inventoryValue));
        res.setProductsInStock(productRepo.countByStatus("IN_STOCK"));
        res.setProductsLowStock(productRepo.countByStatus("LOW_STOCK"));
        res.setProductsOutOfStock(productRepo.countByStatus("OUT_OF_STOCK"));
        res.setProductsHighStock(productRepo.countHighStockProducts());
        res.setSeries(includeSeries ? buildSeries(from, to, g) : List.of());
        res.setTopProducts(buildTopProducts(from, to));   // ← NEW
        return res;
    }

    // ── Product ranking ───────────────────────────────────────────────────────

    /**
     * Returns every product that had at least one sale in the window, ordered
     * by revenue DESC.  The frontend slices top-3 / bottom-3 from this list.
     */
    private List<TopProductEntry> buildTopProducts(LocalDate from, LocalDate to) {
        List<Object[]> rows = saleRepo.findProductRevenueBetween(from, to);
        List<TopProductEntry> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            String name    = row[0] != null ? row[0].toString() : "Unknown";
            double revenue = toDouble(row[1]);
            long   q       = toLong(row[2]);
            if (revenue > 0) {
                result.add(new TopProductEntry(name, round2(revenue), q));
            }
        }
        return result;
    }

    // ── Time-series dispatcher ────────────────────────────────────────────────

    private List<TimeSeriesPoint> buildSeries(LocalDate from, LocalDate to, String granularity) {
        if ("hour".equals(granularity) && from.equals(to)) {
            return hourlySeries(from);
        }
        if ("month".equals(granularity)) {
            return monthlySeries(from, to);
        }
        return dailySeries(from, to);
    }

    // ── Daily series ──────────────────────────────────────────────────────────

    private List<TimeSeriesPoint> dailySeries(LocalDate from, LocalDate to) {
        List<Object[]> rows = saleRepo.sumBySaleDate(from, to);
        Map<LocalDate, double[]> byDay = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate d   = (LocalDate) row[0];
            double    rev = toDouble(row[1]);
            long      q   = toLong(row[2]);
            byDay.put(d, new double[]{ rev, q });
        }

        Map<LocalDate, Double> cogsByDay = mapDateToDouble(saleRepo.sumCogsBySaleDate(from, to));
        Map<LocalDate, Double> expByDay  = mapDateToDouble(expenseRepo.sumByExpenseDate(from, to));

        List<TimeSeriesPoint> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            double[] v      = byDay.getOrDefault(d, new double[]{ 0.0, 0.0 });
            double   cogsD  = nz(cogsByDay.get(d));
            double   expD   = nz(expByDay.get(d));
            double   gp     = v[0] - cogsD;
            double   np     = gp - expD;

            TimeSeriesPoint p = new TimeSeriesPoint();
            p.setLabel(d.format(DAY_LABEL));
            p.setDateKey(d.toString());
            p.setRevenue(round2(v[0]));
            p.setQuantity((long) v[1]);
            p.setProfit(round2(Math.max(0, np)));
            p.setLoss(round2(Math.max(0, -np)));
            out.add(p);
        }
        return out;
    }

    // ── Monthly series ────────────────────────────────────────────────────────

    private List<TimeSeriesPoint> monthlySeries(LocalDate from, LocalDate to) {
        List<Object[]> rows = saleRepo.sumByYearMonth(from, to);
        Map<String, double[]> byYm = new HashMap<>();
        for (Object[] row : rows) {
            int    y   = ((Number) row[0]).intValue();
            int    m   = ((Number) row[1]).intValue();
            double rev = toDouble(row[2]);
            long   q   = toLong(row[3]);
            byYm.put(ymKey(y, m), new double[]{ rev, q });
        }

        Map<String, Double> cogsYm = mapYearMonthToDouble(saleRepo.sumCogsByYearMonth(from, to));
        Map<String, Double> expYm  = mapYearMonthToDouble(expenseRepo.sumExpenseByYearMonth(from, to));

        List<TimeSeriesPoint> out = new ArrayList<>();
        LocalDate cursor = from.withDayOfMonth(1);
        LocalDate end    = to.withDayOfMonth(1);
        while (!cursor.isAfter(end)) {
            String   key   = ymKey(cursor.getYear(), cursor.getMonthValue());
            double[] v     = byYm.getOrDefault(key, new double[]{ 0.0, 0.0 });
            double   cogsM = nz(cogsYm.get(key));
            double   expM  = nz(expYm.get(key));
            double   gp    = v[0] - cogsM;
            double   np    = gp - expM;

            TimeSeriesPoint p = new TimeSeriesPoint();
            p.setLabel(cursor.format(MONTH_LABEL));
            p.setDateKey(cursor.toString());
            p.setRevenue(round2(v[0]));
            p.setQuantity((long) v[1]);
            p.setProfit(round2(Math.max(0, np)));
            p.setLoss(round2(Math.max(0, -np)));
            out.add(p);

            cursor = cursor.plusMonths(1);
        }
        return out;
    }

    // ── Hourly series ─────────────────────────────────────────────────────────

    private List<TimeSeriesPoint> hourlySeries(LocalDate day) {
        List<Sale> sales       = saleRepo.findBySaleDateBetween(day, day);
        double[]   revByHour   = new double[24];
        long[]     qtyByHour   = new long[24];

        for (Sale s : sales) {
            int h = s.getSaleTime() != null ? s.getSaleTime().getHour() : 0;
            h = Math.max(0, Math.min(23, h));
            revByHour[h] += nz(s.getTotal());
            qtyByHour[h] += s.getQuantity() == null ? 0 : s.getQuantity();
        }

        List<TimeSeriesPoint> out = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            double cogsH = hourlyCogsApprox(sales, h);
            double gp    = revByHour[h] - cogsH;

            TimeSeriesPoint p = new TimeSeriesPoint();
            p.setHour(h);
            p.setLabel(formatHour(h));
            p.setDateKey(day.toString());
            p.setRevenue(round2(revByHour[h]));
            p.setQuantity(qtyByHour[h]);
            p.setProfit(round2(Math.max(0,  gp)));
            p.setLoss(round2(Math.max(0,   -gp)));
            out.add(p);
        }
        return out;
    }

    /** Per-sale COGS rolled up to a given hour bucket. */
    private static double hourlyCogsApprox(List<Sale> sales, int hour) {
        double sum = 0;
        for (Sale s : sales) {
            int h = s.getSaleTime() == null ? 0 : s.getSaleTime().getHour();
            if (h != hour) continue;
            double cost = (s.getProduct() != null && s.getProduct().getCost() != null)
                ? s.getProduct().getCost() : 0;
            int q = s.getQuantity() == null ? 0 : s.getQuantity();
            sum += cost * q;
        }
        return sum;
    }

    // ── Mapping helper───────────────────────────────────────────────────────

    private static Map<LocalDate, Double> mapDateToDouble(List<Object[]> rows) {
        Map<LocalDate, Double> m = new HashMap<>();
        if (rows == null) return m;
        for (Object[] row : rows) {
            if (row[0] instanceof LocalDate d) {
                m.put(d, toDouble(row[1]));
            }
        }
        return m;
    }

    private static Map<String, Double> mapYearMonthToDouble(List<Object[]> rows) {
        Map<String, Double> m = new HashMap<>();
        if (rows == null) return m;
        for (Object[] row : rows) {
            int y  = ((Number) row[0]).intValue();
            int mo = ((Number) row[1]).intValue();
            m.put(ymKey(y, mo), toDouble(row[2]));
        }
        return m;
    }

    private static String ymKey(int y, int m) {
        return y + "-" + String.format("%02d", m);
    }

    // ── Arithmetic helpers ────────────────────────────────────────────────────

    private static double nz(Double v)     { return v == null ? 0.0 : v; }
    private static long   nzLong(Long v)   { return v == null ? 0L  : v; }

    private static double toDouble(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.doubleValue();
        return 0;
    }

    private static long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        return 0L;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String formatHour(int h) {
        int hr = h % 12;
        if (hr == 0) hr = 12;
        return hr + (h < 12 ? "am" : "pm");
    }
}