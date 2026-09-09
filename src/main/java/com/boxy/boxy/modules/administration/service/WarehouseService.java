package com.boxy.boxy.modules.administration.service;

import com.boxy.boxy.core.exception.BusinessException;
import com.boxy.boxy.core.exception.ResourceNotFoundException;
import com.boxy.boxy.modules.administration.dto.CreateWarehouseRequest;
import com.boxy.boxy.modules.administration.dto.WarehouseDto;
import com.boxy.boxy.modules.administration.entity.Branch;
import com.boxy.boxy.modules.administration.entity.Warehouse;
import com.boxy.boxy.modules.administration.repository.BranchRepository;
import com.boxy.boxy.modules.administration.repository.WarehouseRepository;
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

    @Transactional(readOnly = true)
    public List<WarehouseDto> getAllWarehouses() {
        return warehouseRepository.findAll().stream()
                .filter(w -> w.getDeletedAt() == null)
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public WarehouseDto getWarehouseById(Long id) {
        Warehouse warehouse = warehouseRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", id));
        return toDto(warehouse);
    }

    @Transactional
    public WarehouseDto createWarehouse(CreateWarehouseRequest request) {
        Branch branch = branchRepository.findByIdAndDeletedAtIsNull(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch", request.getBranchId()));

        boolean active = request.getStatus() == null || "active".equalsIgnoreCase(request.getStatus());

        Warehouse warehouse = Warehouse.builder()
                .branch(branch)
                .code(request.getCode().trim().toUpperCase())
                .name(request.getName().trim())
                .isDefault(Boolean.TRUE.equals(request.getIsDefault()))
                .isActive(active)
                .build();

        return toDto(warehouseRepository.save(warehouse));
    }

    @Transactional
    public WarehouseDto updateWarehouse(Long id, CreateWarehouseRequest request) {
        Warehouse warehouse = warehouseRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", id));

        if (request.getBranchId() != null && !request.getBranchId().equals(warehouse.getBranch().getId())) {
            Branch branch = branchRepository.findByIdAndDeletedAtIsNull(request.getBranchId())
                    .orElseThrow(() -> new ResourceNotFoundException("Branch", request.getBranchId()));
            warehouse.setBranch(branch);
        }

        if (request.getCode() != null) {
            warehouse.setCode(request.getCode().trim().toUpperCase());
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

        return toDto(warehouseRepository.save(warehouse));
    }

    public WarehouseDto toDto(Warehouse w) {
        boolean active = Boolean.TRUE.equals(w.getIsActive());
        Branch branch = w.getBranch();
        int productCount = 0;
        BigDecimal stockValue = BigDecimal.ZERO;
        try {
            productCount = stockLevelRepository.countDistinctProductsByWarehouseId(w.getId());
            stockValue = stockLevelRepository.calculateStockValueByWarehouseId(w.getId());
        } catch (Exception ignored) {
        }

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
                .productCount(productCount)
                .stockValue(stockValue != null ? stockValue : BigDecimal.ZERO)
                .build();
    }
}
