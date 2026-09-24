package com.boxy.boxy.modules.administration.service;

import com.boxy.boxy.core.exception.BusinessException;
import com.boxy.boxy.core.exception.ResourceNotFoundException;
import com.boxy.boxy.core.security.SecurityUtils;
import com.boxy.boxy.modules.administration.dto.CreateWarehouseRequest;
import com.boxy.boxy.modules.administration.dto.WarehouseDto;
import com.boxy.boxy.modules.administration.entity.Branch;
import com.boxy.boxy.modules.administration.entity.Warehouse;
import com.boxy.boxy.modules.administration.repository.BranchRepository;
import com.boxy.boxy.modules.administration.repository.WarehouseRepository;
import com.boxy.boxy.modules.catalog.repository.ProductRepository;
import com.boxy.boxy.modules.inventory.repository.StockLevelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final BranchRepository branchRepository;
    private final StockLevelRepository stockLevelRepository;
    private final ProductRepository productRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<WarehouseDto> getAllWarehouses() {
        Long companyId = SecurityUtils.requireCurrentCompanyId();
        return warehouseRepository.findByBranchCompanyIdAndDeletedAtIsNull(companyId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public WarehouseDto getWarehouseById(Long id) {
        return toDto(findOwnedWarehouse(id));
    }

    @Transactional
    public WarehouseDto createWarehouse(CreateWarehouseRequest request) {
        Long companyId = SecurityUtils.requireCurrentCompanyId();
        Branch branch = branchRepository.findByIdAndCompanyIdAndDeletedAtIsNull(request.getBranchId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", request.getBranchId()));

        String code = request.getCode().trim().toUpperCase();
        warehouseRepository.findByBranchCompanyIdAndCodeIgnoreCaseAndDeletedAtIsNull(companyId, code)
                .ifPresent(existing -> {
                    throw new BusinessException("WAREHOUSE_CODE_EXISTS", "A warehouse with code '" + code + "' already exists.");
                });

        boolean active = request.getStatus() == null || "active".equalsIgnoreCase(request.getStatus());

        Warehouse warehouse = Warehouse.builder()
                .branch(branch)
                .code(code)
                .name(request.getName().trim())
                .isDefault(Boolean.TRUE.equals(request.getIsDefault()))
                .isActive(active)
                .build();

        Warehouse saved = warehouseRepository.save(warehouse);
        auditLogService.record("Almacén creado", "Almacén", String.valueOf(saved.getId()), saved.getName(),
                null, "código: " + saved.getCode());
        return toDto(saved);
    }

    @Transactional
    public WarehouseDto updateWarehouse(Long id, CreateWarehouseRequest request) {
        Long companyId = SecurityUtils.requireCurrentCompanyId();
        Warehouse warehouse = findOwnedWarehouse(id);
        String previous = describeWarehouse(warehouse);

        if (request.getBranchId() != null && !request.getBranchId().equals(warehouse.getBranch().getId())) {
            Branch branch = branchRepository.findByIdAndCompanyIdAndDeletedAtIsNull(request.getBranchId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Branch", request.getBranchId()));
            warehouse.setBranch(branch);
        }

        if (request.getCode() != null) {
            String code = request.getCode().trim().toUpperCase();
            if (!code.equalsIgnoreCase(warehouse.getCode())) {
                warehouseRepository.findByBranchCompanyIdAndCodeIgnoreCaseAndDeletedAtIsNull(companyId, code)
                        .ifPresent(existing -> {
                            throw new BusinessException("WAREHOUSE_CODE_EXISTS", "A warehouse with code '" + code + "' already exists.");
                        });
                warehouse.setCode(code);
            }
        }
        if (request.getName() != null) {
            warehouse.setName(request.getName().trim());
        }
        if (request.getStatus() != null) {
            warehouse.setIsActive("active".equalsIgnoreCase(request.getStatus()));
        }
        if (request.getIsDefault() != null) {
            warehouse.setIsDefault(request.getIsDefault());
        }

        Warehouse saved = warehouseRepository.save(warehouse);
        auditLogService.record("Almacén actualizado", "Almacén", String.valueOf(saved.getId()), saved.getName(),
                previous, describeWarehouse(saved));
        return toDto(saved);
    }

    /** 404s (not 403) on a warehouse belonging to another company — same treatment as "doesn't exist". */
    private Warehouse findOwnedWarehouse(Long id) {
        Long companyId = SecurityUtils.requireCurrentCompanyId();
        return warehouseRepository.findByIdAndBranchCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", id));
    }

    private String describeWarehouse(Warehouse w) {
        return "nombre: " + w.getName() + "\ncódigo: " + w.getCode()
                + "\nactivo: " + Boolean.TRUE.equals(w.getIsActive());
    }

    public WarehouseDto toDto(Warehouse w) {
        boolean active = Boolean.TRUE.equals(w.getIsActive());
        Branch branch = w.getBranch();
        // Matches what the warehouse's own detail page lists (every active company
        // product, not just the ones with quantityAvailable > 0) — see
        // InventoryService.getStockLevelsByWarehouse.
        Long companyId = branch != null ? branch.getCompany().getId() : null;
        long productCount = companyId != null ? productRepository.countByCompanyIdAndIsActiveTrueAndDeletedAtIsNull(companyId) : 0;
        BigDecimal stockValue = stockLevelRepository.calculateStockValueByWarehouseId(w.getId());

        return WarehouseDto.builder()
                .id(w.getId())
                .code(w.getCode())
                .name(w.getName())
                .branchId(branch != null ? branch.getId() : null)
                .branchName(branch != null ? branch.getName() : "—")
                .branchCode(branch != null ? branch.getCode() : "—")
                .isDefault(Boolean.TRUE.equals(w.getIsDefault()))
                .isActive(active)
                .status(active ? "active" : "inactive")
                .productCount((int) productCount)
                .stockValue(stockValue != null ? stockValue : BigDecimal.ZERO)
                .build();
    }
}
