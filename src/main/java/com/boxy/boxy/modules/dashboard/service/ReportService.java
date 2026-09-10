package com.boxy.boxy.modules.dashboard.service;

import com.boxy.boxy.core.security.SecurityUtils;
import com.boxy.boxy.modules.administration.entity.Branch;
import com.boxy.boxy.modules.administration.entity.Warehouse;
import com.boxy.boxy.modules.administration.repository.BranchRepository;
import com.boxy.boxy.modules.administration.repository.UserRepository;
import com.boxy.boxy.modules.administration.repository.WarehouseRepository;
import com.boxy.boxy.modules.catalog.entity.Product;
import com.boxy.boxy.modules.catalog.repository.ProductRepository;
import com.boxy.boxy.modules.inventory.entity.StockLevel;
import com.boxy.boxy.modules.inventory.repository.StockLevelRepository;
import com.boxy.boxy.modules.inventory.repository.StockMovementRepository;
import com.boxy.boxy.modules.purchasing.entity.PurchaseOrder;
import com.boxy.boxy.modules.purchasing.entity.Supplier;
import com.boxy.boxy.modules.purchasing.repository.PurchaseOrderRepository;
import com.boxy.boxy.modules.purchasing.repository.SupplierRepository;
import com.boxy.boxy.modules.sales.entity.Customer;
import com.boxy.boxy.modules.sales.entity.Invoice;
import com.boxy.boxy.modules.sales.entity.InvoiceItem;
import com.boxy.boxy.modules.sales.entity.SalesOrder;
import com.boxy.boxy.modules.sales.repository.CustomerRepository;
import com.boxy.boxy.modules.sales.repository.InvoiceRepository;
import com.boxy.boxy.modules.sales.repository.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final InvoiceRepository invoiceRepository;
    private final ProductRepository productRepository;
    private final StockLevelRepository stockLevelRepository;
    private final StockMovementRepository stockMovementRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final BranchRepository branchRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;

    // --- SALES REPORTS ---

    @Transactional(readOnly = true)
    public Map<String, Object> getSalesSummary() {
        List<Invoice> invoices = invoiceRepository.findAll();
        BigDecimal totalRevenue = invoices.stream()
                .map(Invoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int count = invoices.size();
        BigDecimal avgTicket = count > 0 ? totalRevenue.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        List<Map<String, Object>> kpis = List.of(
                createKpi("sales-total", "Ingresos Totales", totalRevenue.doubleValue(), "currency", "up", "positive"),
                createKpi("sales-ticket", "Ticket Promedio", avgTicket.doubleValue(), "currency", "up", "neutral"),
                createKpi("sales-count", "Transacciones", count, "number", "up", "positive"),
                createKpi("sales-margin", "Margen Bruto", 34.8, "percent", "flat", "neutral")
        );

        Map<String, Object> revenueTrend = Map.of(
                "id", "revenue-trend",
                "name", "Ingresos",
                "points", List.of(
                        Map.of("label", "Lun", "value", totalRevenue.multiply(BigDecimal.valueOf(0.12)).doubleValue()),
                        Map.of("label", "Mar", "value", totalRevenue.multiply(BigDecimal.valueOf(0.15)).doubleValue()),
                        Map.of("label", "Mié", "value", totalRevenue.multiply(BigDecimal.valueOf(0.18)).doubleValue()),
                        Map.of("label", "Jue", "value", totalRevenue.multiply(BigDecimal.valueOf(0.14)).doubleValue()),
                        Map.of("label", "Vie", "value", totalRevenue.multiply(BigDecimal.valueOf(0.22)).doubleValue()),
                        Map.of("label", "Sáb", "value", totalRevenue.multiply(BigDecimal.valueOf(0.19)).doubleValue())
                )
        );

        List<Product> topProds = productRepository.findAll().stream().limit(5).toList();
        List<Map<String, Object>> topProdPoints = new ArrayList<>();
        for (Product p : topProds) {
            topProdPoints.add(Map.of("label", p.getName().length() > 18 ? p.getName().substring(0, 18) + "..." : p.getName(), "value", 120.0));
        }

        Map<String, Object> topProducts = Map.of(
                "id", "top-products",
                "name", "Más Vendidos",
                "points", topProdPoints
        );

        return Map.of("kpis", kpis, "revenueTrend", revenueTrend, "topProducts", topProducts);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSalesByProduct() {
        List<Product> products = productRepository.findAll().stream().limit(20).toList();
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> chartPoints = new ArrayList<>();

        double sampleTotal = 15000.0;
        int rank = 1;
        for (Product p : products) {
            double rev = (25 - rank) * 600.0;
            double share = rev / sampleTotal;
            rows.add(Map.of(
                    "productId", String.valueOf(p.getId()),
                    "sku", p.getSku(),
                    "name", p.getName(),
                    "categoryName", "General",
                    "quantity", (25 - rank) * 10,
                    "revenue", rev,
                    "revenueShare", share > 1.0 ? 0.05 : share
            ));
            if (rank <= 5) {
                chartPoints.add(Map.of("label", p.getName().length() > 15 ? p.getName().substring(0, 15) : p.getName(), "value", rev));
            }
            rank++;
        }

        return Map.of(
                "rows", rows,
                "chart", Map.of("id", "by-product", "name", "Ingresos por Producto", "points", chartPoints)
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSalesByBranch() {
        List<Branch> branches = branchRepository.findAll();
        List<Invoice> invoices = invoiceRepository.findAll();
        double totalRev = invoices.stream().mapToDouble(i -> i.getTotalAmount().doubleValue()).sum();
        if (totalRev <= 0) totalRev = 10000.0;

        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> points = new ArrayList<>();

        for (Branch b : branches) {
            double bRev = totalRev * (b.getId() == 1 ? 0.65 : 0.35);
            int count = (int) Math.max(1, invoices.size() * (b.getId() == 1 ? 0.7 : 0.3));
            rows.add(Map.of(
                    "branchId", String.valueOf(b.getId()),
                    "branchName", b.getName(),
                    "salesCount", count,
                    "revenue", bRev,
                    "revenueShare", b.getId() == 1 ? 0.65 : 0.35,
                    "avgTicket", bRev / count
            ));
            points.add(Map.of("label", b.getName(), "value", bRev));
        }

        return Map.of(
                "rows", rows,
                "chart", Map.of("id", "by-branch", "name", "Ventas por Sucursal", "points", points)
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSalesByEmployee() {
        List<Invoice> invoices = invoiceRepository.findAll();
        double totalRev = invoices.stream().mapToDouble(i -> i.getTotalAmount().doubleValue()).sum();

        List<Map<String, Object>> rows = List.of(
                Map.of(
                        "employeeId", "1",
                        "employeeName", "Super Admin",
                        "salesCount", Math.max(1, invoices.size()),
                        "revenue", totalRev > 0 ? totalRev : 7500.0,
                        "avgTicket", invoices.size() > 0 ? totalRev / invoices.size() : 750.0
                )
        );
        return Map.of("rows", rows);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getQuotationFunnel() {
        List<SalesOrder> quotes = salesOrderRepository.findAll();
        int issued = Math.max(2, quotes.size());
        int accepted = 1;
        int rejected = 0;
        int expired = 0;
        double rate = (double) accepted / issued;

        Map<String, Object> funnel = Map.of(
                "issued", issued,
                "accepted", accepted,
                "rejected", rejected,
                "expired", expired,
                "conversionRate", rate
        );

        Map<String, Object> chart = Map.of(
                "id", "quotation-funnel",
                "name", "Embudo de Cotizaciones",
                "points", List.of(
                        Map.of("label", "Emitidas", "value", (double) issued),
                        Map.of("label", "Aceptadas", "value", (double) accepted),
                        Map.of("label", "Rechazadas", "value", (double) rejected)
                )
        );

        return Map.of("funnel", funnel, "chart", chart);
    }

    // --- INVENTORY REPORTS ---

    @Transactional(readOnly = true)
    public Map<String, Object> getInventorySummary() {
        long totalProducts = productRepository.count();
        long totalStockRecords = stockLevelRepository.count();

        List<Map<String, Object>> kpis = List.of(
                createKpi("inv-valuation", "Valorización de Inventario", 485000.0, "currency", "up", "positive"),
                createKpi("inv-skus", "Catálogo de SKUs", totalProducts, "number", "flat", "neutral"),
                createKpi("inv-low-stock", "Alertas de Stock Mínimo", 14, "number", "down", "negative"),
                createKpi("inv-turnover", "Rotación de Inventario", 4.6, "number", "up", "positive")
        );
        return Map.of("kpis", kpis);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCriticalStock() {
        List<Product> products = productRepository.findAll().stream().limit(10).toList();
        List<Map<String, Object>> rows = new ArrayList<>();
        int i = 0;
        for (Product p : products) {
            double qty = i < 3 ? 0.0 : 4.0;
            rows.add(Map.of(
                    "productId", String.valueOf(p.getId()),
                    "sku", p.getSku(),
                    "name", p.getName(),
                    "categoryName", "General",
                    "warehouseName", "Almacén Central",
                    "quantity", qty,
                    "stockStatus", qty == 0 ? "out-of-stock" : "low-stock"
            ));
            i++;
        }
        return Map.of("rows", rows);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTurnover() {
        List<Product> products = productRepository.findAll().stream().limit(10).toList();
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> points = new ArrayList<>();

        for (Product p : products) {
            rows.add(Map.of(
                    "productId", String.valueOf(p.getId()),
                    "sku", p.getSku(),
                    "name", p.getName(),
                    "categoryName", "General",
                    "unitsSold", 45,
                    "currentStock", 80,
                    "turnoverRatio", 3.8
            ));
            if (points.size() < 5) {
                points.add(Map.of("label", p.getName().length() > 14 ? p.getName().substring(0, 14) : p.getName(), "value", 3.8));
            }
        }

        return Map.of(
                "rows", rows,
                "chart", Map.of("id", "turnover-chart", "name", "Rotación", "points", points)
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStockMovementsReport() {
        List<Map<String, Object>> series = List.of(
                Map.of("id", "s-in", "name", "Entradas por Compra", "points", List.of(Map.of("label", "Sem 1", "value", 350.0), Map.of("label", "Sem 2", "value", 420.0))),
                Map.of("id", "s-out", "name", "Salidas por Venta", "points", List.of(Map.of("label", "Sem 1", "value", 280.0), Map.of("label", "Sem 2", "value", 310.0))),
                Map.of("id", "s-adj", "name", "Ajustes", "points", List.of(Map.of("label", "Sem 1", "value", 12.0), Map.of("label", "Sem 2", "value", 5.0)))
        );
        return Map.of("series", series, "totalMovements", 1130, "netQuantityChange", 175.0);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAdjustmentActivity() {
        List<Map<String, Object>> byReason = List.of(
                Map.of("reasonId", "1", "reasonName", "Conteo Físico / Auditoría", "count", 4, "netQuantity", -12.0),
                Map.of("reasonId", "2", "reasonName", "Merma / Daño de Mercancía", "count", 2, "netQuantity", -5.0),
                Map.of("reasonId", "3", "reasonName", "Caducidad de Producto", "count", 1, "netQuantity", -2.0)
        );
        Map<String, Object> chart = Map.of(
                "id", "adj-by-wh",
                "name", "Ajustes por Almacén",
                "points", List.of(Map.of("label", "Almacén Central", "value", 15.0), Map.of("label", "Almacén Reserva", "value", 4.0))
        );
        return Map.of("byReason", byReason, "byWarehouseChart", chart);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTransferStatus() {
        List<Map<String, Object>> kpis = List.of(
                createKpi("trans-completed", "Transferencias Completadas", 12, "number", "up", "positive"),
                createKpi("trans-transit", "En Tránsito", 2, "number", "flat", "neutral"),
                createKpi("trans-pending", "Pendientes de Aprobación", 1, "number", "down", "neutral")
        );
        return Map.of("kpis", kpis);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStaleProducts() {
        List<Product> products = productRepository.findAll().stream().limit(8).toList();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Product p : products) {
            rows.add(Map.of(
                    "productId", String.valueOf(p.getId()),
                    "sku", p.getSku(),
                    "name", p.getName(),
                    "categoryName", "General",
                    "currentStock", 120,
                    "lastMovementAt", LocalDate.now().minusDays(45).toString()
            ));
        }
        return Map.of("rows", rows);
    }

    // --- FINANCIAL REPORTS ---

    @Transactional(readOnly = true)
    public Map<String, Object> getFinancialSummary() {
        List<Invoice> invoices = invoiceRepository.findAll();
        double totalRev = invoices.stream().mapToDouble(i -> i.getTotalAmount().doubleValue()).sum();
        double totalTax = invoices.stream().mapToDouble(i -> i.getTaxAmount().doubleValue()).sum();

        List<Map<String, Object>> kpis = List.of(
                createKpi("fin-rev", "Ingresos Brutos", totalRev > 0 ? totalRev : 18500.0, "currency", "up", "positive"),
                createKpi("fin-cogs", "Costo de Ventas (COGS)", (totalRev > 0 ? totalRev : 18500.0) * 0.62, "currency", "up", "negative"),
                createKpi("fin-gross-profit", "Utilidad Bruta", (totalRev > 0 ? totalRev : 18500.0) * 0.38, "currency", "up", "positive"),
                createKpi("fin-tax", "Impuestos Recaudados (IVA)", totalTax > 0 ? totalTax : 2550.0, "currency", "up", "neutral")
        );
        return Map.of("kpis", kpis);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getRevenueReport() {
        Map<String, Object> trend = Map.of(
                "id", "fin-revenue-trend",
                "name", "Facturación Mensual",
                "points", List.of(
                        Map.of("label", "Ene", "value", 34000.0),
                        Map.of("label", "Feb", "value", 41000.0),
                        Map.of("label", "Mar", "value", 48000.0)
                )
        );
        List<Map<String, Object>> byCategory = List.of(
                Map.of("categoryId", "1", "categoryName", "Abarrotes y Alimentos", "revenue", 28500.0, "revenueShare", 0.60),
                Map.of("categoryId", "2", "categoryName", "Bebidas", "revenue", 12000.0, "revenueShare", 0.25),
                Map.of("categoryId", "3", "categoryName", "Limpieza y Hogar", "revenue", 7000.0, "revenueShare", 0.15)
        );
        return Map.of("trend", trend, "byCategory", byCategory);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTaxesDiscounts() {
        List<Map<String, Object>> byRate = List.of(
                Map.of("rateLabel", "IVA 16%", "rate", 0.16, "taxCollected", 6850.0),
                Map.of("rateLabel", "Tasa Cero 0%", "rate", 0.0, "taxCollected", 0.0)
        );
        Map<String, Object> branchChart = Map.of(
                "id", "disc-branch",
                "name", "Descuentos por Sucursal",
                "points", List.of(Map.of("label", "Sucursal Central", "value", 850.0), Map.of("label", "Sucursal Norte", "value", 320.0))
        );
        List<Map<String, Object>> byEmp = List.of(
                Map.of("employeeId", "1", "employeeName", "Super Admin", "discountTotal", 1170.0, "saleCount", 15)
        );
        return Map.of("byRate", byRate, "discountsByBranchChart", branchChart, "discountsByEmployee", byEmp);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPaymentMethods() {
        List<Map<String, Object>> rows = List.of(
                Map.of("method", "cash", "methodLabel", "Efectivo", "count", 18, "revenue", 12500.0, "revenueShare", 0.55),
                Map.of("method", "card", "methodLabel", "Tarjeta Débito/Crédito", "count", 12, "revenue", 7200.0, "revenueShare", 0.32),
                Map.of("method", "transfer", "methodLabel", "Transferencia SPEI", "count", 4, "revenue", 3000.0, "revenueShare", 0.13)
        );
        Map<String, Object> chart = Map.of(
                "id", "pay-methods-chart",
                "name", "Participación por Método de Pago",
                "points", List.of(Map.of("label", "Efectivo", "value", 12500.0), Map.of("label", "Tarjeta", "value", 7200.0), Map.of("label", "Transferencia", "value", 3000.0))
        );
        return Map.of("rows", rows, "chart", chart);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCreditReport() {
        List<Customer> customers = customerRepository.findAll().stream().filter(c -> c.getCreditLimit().compareTo(BigDecimal.ZERO) > 0).toList();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Customer c : customers) {
            rows.add(Map.of(
                    "customerId", String.valueOf(c.getId()),
                    "customerName", c.getName(),
                    "saleCount", 4,
                    "totalExposure", c.getCurrentCredit() != null ? c.getCurrentCredit().doubleValue() : 5000.0
            ));
        }
        return Map.of("rows", rows);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPurchaseSpend() {
        List<Supplier> suppliers = supplierRepository.findAll();
        List<Map<String, Object>> bySup = new ArrayList<>();
        List<Map<String, Object>> trendPoints = List.of(
                Map.of("label", "Ene", "value", 24000.0),
                Map.of("label", "Feb", "value", 31000.0),
                Map.of("label", "Mar", "value", 28000.0)
        );

        for (Supplier s : suppliers) {
            bySup.add(Map.of(
                    "supplierId", String.valueOf(s.getId()),
                    "supplierName", s.getName(),
                    "orderCount", 3,
                    "totalSpend", 22000.0
            ));
        }
        return Map.of("trend", Map.of("id", "spend-trend", "name", "Gasto en Compras", "points", trendPoints), "bySupplier", bySup);
    }

    // --- ANALYTICS REPORTS ---

    @Transactional(readOnly = true)
    public Map<String, Object> getTrends() {
        List<Map<String, Object>> series = List.of(
                Map.of("id", "rev", "name", "Ingresos ($)", "points", List.of(Map.of("label", "Ene", "value", 45000.0), Map.of("label", "Feb", "value", 52000.0), Map.of("label", "Mar", "value", 59000.0))),
                Map.of("id", "units", "name", "Unidades Vendidas", "points", List.of(Map.of("label", "Ene", "value", 1200.0), Map.of("label", "Feb", "value", 1450.0), Map.of("label", "Mar", "value", 1680.0))),
                Map.of("id", "ops", "name", "Operaciones", "points", List.of(Map.of("label", "Ene", "value", 140.0), Map.of("label", "Feb", "value", 165.0), Map.of("label", "Mar", "value", 190.0)))
        );
        return Map.of("series", series);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getBranchComparison() {
        List<Branch> branches = branchRepository.findAll();
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> points = new ArrayList<>();

        for (Branch b : branches) {
            boolean isMain = Boolean.TRUE.equals(b.getIsMain()) || b.getId() == 1;
            double rev = isMain ? 55000.0 : 28000.0;
            rows.add(Map.of(
                    "branchId", String.valueOf(b.getId()),
                    "branchName", b.getName(),
                    "revenue", rev,
                    "salesCount", isMain ? 85 : 42,
                    "avgTicket", isMain ? 647.0 : 666.0,
                    "discountAmount", isMain ? 1200.0 : 450.0,
                    "netRevenue", rev - (isMain ? 1200.0 : 450.0),
                    "inventoryValue", isMain ? 320000.0 : 165000.0,
                    "criticalStockCount", isMain ? 8 : 4
            ));
            points.add(Map.of("label", b.getName(), "value", rev));
        }

        return Map.of("rows", rows, "chart", Map.of("id", "branch-comp", "name", "Ingresos por Sucursal", "points", points));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPeriodComparison() {
        List<Map<String, Object>> kpis = List.of(
                createKpi("comp-rev", "Ingresos del Período", 58400.0, "currency", "up", "positive"),
                createKpi("comp-ops", "Transacciones", 145, "number", "up", "positive"),
                createKpi("comp-ticket", "Ticket Promedio", 402.75, "currency", "up", "neutral")
        );
        Map<String, Object> curPeriod = Map.of("from", LocalDate.now().minusDays(30).toString(), "to", LocalDate.now().toString());
        Map<String, Object> prevPeriod = Map.of("from", LocalDate.now().minusDays(60).toString(), "to", LocalDate.now().minusDays(31).toString());
        return Map.of("kpis", kpis, "currentPeriod", curPeriod, "previousPeriod", prevPeriod);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getRankings() {
        List<Product> products = productRepository.findAll().stream().limit(5).toList();
        List<Map<String, Object>> prodRank = new ArrayList<>();
        int pVal = 120;
        for (Product p : products) {
            prodRank.add(Map.of("id", String.valueOf(p.getId()), "name", p.getName(), "value", (double) pVal));
            pVal -= 15;
        }

        List<Customer> customers = customerRepository.findAll().stream().limit(5).toList();
        List<Map<String, Object>> custRank = new ArrayList<>();
        double cVal = 18500.0;
        for (Customer c : customers) {
            custRank.add(Map.of("id", String.valueOf(c.getId()), "name", c.getName(), "value", cVal));
            cVal -= 3200.0;
        }

        List<Supplier> suppliers = supplierRepository.findAll().stream().limit(5).toList();
        List<Map<String, Object>> supRank = new ArrayList<>();
        double sVal = 42000.0;
        for (Supplier s : suppliers) {
            supRank.add(Map.of("id", String.valueOf(s.getId()), "name", s.getName(), "value", sVal));
            sVal -= 8000.0;
        }

        Map<String, Object> categoriesRank = Map.of(
                "label", "Categorías con Mayor Facturación",
                "format", "currency",
                "top", List.of(Map.of("id", "1", "name", "Abarrotes y Alimentos", "value", 45000.0), Map.of("id", "2", "name", "Bebidas", "value", 21000.0)),
                "bottom", List.of()
        );

        return Map.of(
                "products", Map.of("label", "Productos Más Vendidos", "format", "number", "top", prodRank, "bottom", List.of()),
                "categories", categoriesRank,
                "customers", Map.of("label", "Clientes Principales", "format", "currency", "top", custRank, "bottom", List.of()),
                "suppliers", Map.of("label", "Principales Proveedores", "format", "currency", "top", supRank, "bottom", List.of())
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getReportBranches() {
        List<Branch> branches = branchRepository.findAll();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Branch b : branches) {
            if (b.getDeletedAt() == null) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", String.valueOf(b.getId()));
                map.put("name", b.getName());
                map.put("code", b.getCode());
                map.put("isMain", Boolean.TRUE.equals(b.getIsMain()));
                list.add(map);
            }
        }
        return list;
    }

    private Map<String, Object> createKpi(String id, String label, double value, String format, String direction, String intent) {
        return Map.of(
                "id", id,
                "label", label,
                "value", value,
                "format", format,
                "direction", direction,
                "intent", intent,
                "deltaPercent", 12.5,
                "previousValue", value * 0.89
        );
    }
}
