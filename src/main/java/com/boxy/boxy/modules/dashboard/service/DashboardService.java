package com.boxy.boxy.modules.dashboard.service;

import com.boxy.boxy.core.security.SecurityUtils;
import com.boxy.boxy.modules.catalog.repository.ProductRepository;
import com.boxy.boxy.modules.dashboard.dto.DashboardSummaryDto;
import com.boxy.boxy.modules.inventory.entity.StockLevel;
import com.boxy.boxy.modules.inventory.repository.StockLevelRepository;
import com.boxy.boxy.modules.sales.entity.Invoice;
import com.boxy.boxy.modules.sales.repository.CustomerRepository;
import com.boxy.boxy.modules.sales.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final StockLevelRepository stockLevelRepository;

    @Transactional(readOnly = true)
    public DashboardSummaryDto getSummary(String branchId) {
        String companyId = SecurityUtils.getCurrentCompanyId();

        long productCount = productRepository.count();
        long customerCount = customerRepository.count();

        List<Invoice> recentInvoices;
        if (branchId != null) {
            recentInvoices = invoiceRepository.findByBranchIdOrderByCreatedAtDesc(branchId, PageRequest.of(0, 5)).getContent();
        } else {
            recentInvoices = invoiceRepository.findByCompanyIdOrderByCreatedAtDesc(companyId, PageRequest.of(0, 5)).getContent();
        }

        BigDecimal salesToday = recentInvoices.stream()
                .map(Invoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<DashboardSummaryDto.RecentSaleDto> recentSaleDtos = recentInvoices.stream()
                .map(inv -> DashboardSummaryDto.RecentSaleDto.builder()
                        .invoiceId(inv.getId())
                        .invoiceNumber(inv.getSeries() + "-" + inv.getNumber())
                        .customerName(inv.getCustomer().getName())
                        .totalAmount(inv.getTotalAmount())
                        .createdAt(inv.getCreatedAt().toString())
                        .build())
                .toList();

        List<StockLevel> lowStockLevels = branchId != null ? stockLevelRepository.findLowStockByBranch(branchId) : List.of();
        List<DashboardSummaryDto.LowStockAlertDto> alertDtos = lowStockLevels.stream()
                .map(sl -> DashboardSummaryDto.LowStockAlertDto.builder()
                        .productId(sl.getProduct().getId())
                        .productSku(sl.getProduct().getSku())
                        .productName(sl.getProduct().getName())
                        .availableStock(sl.getQuantityAvailable())
                        .minStockAlert(sl.getProduct().getMinStockAlert())
                        .build())
                .toList();

        return DashboardSummaryDto.builder()
                .totalSalesToday(salesToday)
                .totalOrdersToday(recentInvoices.size())
                .totalCustomers(customerCount)
                .totalProducts(productCount)
                .lowStockAlertsCount(alertDtos.size())
                .recentSales(recentSaleDtos)
                .lowStockProducts(alertDtos)
                .build();
    }
}
