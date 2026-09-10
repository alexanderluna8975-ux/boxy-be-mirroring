package com.boxy.boxy.modules.administration.service;

import com.boxy.boxy.core.exception.BusinessException;
import com.boxy.boxy.core.exception.ResourceNotFoundException;
import com.boxy.boxy.core.security.SecurityUtils;
import com.boxy.boxy.modules.administration.dto.BranchDto;
import com.boxy.boxy.modules.administration.dto.CreateBranchRequest;
import com.boxy.boxy.modules.administration.dto.WarehouseDto;
import com.boxy.boxy.modules.administration.entity.Branch;
import com.boxy.boxy.modules.administration.entity.Company;
import com.boxy.boxy.modules.administration.entity.Warehouse;
import com.boxy.boxy.modules.administration.repository.BranchRepository;
import com.boxy.boxy.modules.administration.repository.CompanyRepository;
import com.boxy.boxy.modules.administration.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;
    private final WarehouseRepository warehouseRepository;
    private final CompanyRepository companyRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<BranchDto> getAllBranches() {
        Long companyId = SecurityUtils.requireCurrentCompanyId();
        return branchRepository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public BranchDto getBranchById(Long id) {
        return toDto(findOwnedBranch(id));
    }

    @Transactional
    public BranchDto createBranch(CreateBranchRequest request) {
        Long companyId = SecurityUtils.requireCurrentCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        String code = request.getCode().toUpperCase();
        if (branchRepository.findByCompanyIdAndCodeAndDeletedAtIsNull(companyId, code).isPresent()) {
            throw new BusinessException("BRANCH_CODE_EXISTS", "A branch with code '" + code + "' already exists.");
        }

        Branch branch = Branch.builder()
                .company(company)
                .code(code)
                .name(request.getName())
                .address(request.getAddress())
                .phone(request.getPhone())
                .email(request.getEmail())
                .isMain(request.isMain())
                .isActive(true)
                .build();

        Branch savedBranch = branchRepository.save(branch);

        // Create default warehouse for this branch
        Warehouse defaultWarehouse = Warehouse.builder()
                .branch(savedBranch)
                .code(savedBranch.getCode() + "-MAIN")
                .name(savedBranch.getName() + " Main Storage")
                .isDefault(true)
                .isActive(true)
                .build();
        warehouseRepository.save(defaultWarehouse);

        auditLogService.record("Sucursal creada", "Sucursal", String.valueOf(savedBranch.getId()), savedBranch.getName(),
                null, "código: " + savedBranch.getCode());
        return toDto(savedBranch);
    }

    @Transactional
    public BranchDto updateBranch(Long id, CreateBranchRequest request) {
        Long companyId = SecurityUtils.requireCurrentCompanyId();
        Branch branch = findOwnedBranch(id);
        String previous = describeBranch(branch);

        if (request.getName() != null) branch.setName(request.getName());
        if (request.getCode() != null) {
            String code = request.getCode().toUpperCase();
            if (!code.equalsIgnoreCase(branch.getCode())) {
                branchRepository.findByCompanyIdAndCodeAndDeletedAtIsNull(companyId, code).ifPresent(existing -> {
                    throw new BusinessException("BRANCH_CODE_EXISTS", "A branch with code '" + code + "' already exists.");
                });
                branch.setCode(code);
            }
        }
        if (request.getAddress() != null) branch.setAddress(request.getAddress());
        if (request.getPhone() != null) branch.setPhone(request.getPhone());
        if (request.getEmail() != null) branch.setEmail(request.getEmail());

        Branch saved = branchRepository.save(branch);
        auditLogService.record("Sucursal actualizada", "Sucursal", String.valueOf(saved.getId()), saved.getName(),
                previous, describeBranch(saved));
        return toDto(saved);
    }

    @Transactional
    public BranchDto deactivateBranch(Long id) {
        Branch branch = findOwnedBranch(id);
        boolean wasActive = Boolean.TRUE.equals(branch.getIsActive());
        branch.setIsActive(!wasActive);
        Branch saved = branchRepository.save(branch);
        auditLogService.record(wasActive ? "Sucursal desactivada" : "Sucursal activada", "Sucursal",
                String.valueOf(saved.getId()), saved.getName(),
                wasActive ? "active" : "inactive", wasActive ? "inactive" : "active");
        return toDto(saved);
    }

    /** 404s (not 403) on a branch belonging to another company — same treatment as "doesn't exist". */
    private Branch findOwnedBranch(Long id) {
        Long companyId = SecurityUtils.requireCurrentCompanyId();
        return branchRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", id));
    }

    private String describeBranch(Branch branch) {
        return "nombre: " + branch.getName() + "\ncódigo: " + branch.getCode()
                + "\ndirección: " + (branch.getAddress() != null ? branch.getAddress() : "");
    }

    private BranchDto toDto(Branch branch) {
        List<WarehouseDto> warehouseDtos = warehouseRepository.findByBranchIdAndDeletedAtIsNull(branch.getId()).stream()
                .map(w -> WarehouseDto.builder()
                        .id(w.getId())
                        .code(w.getCode())
                        .name(w.getName())
                        .isDefault(Boolean.TRUE.equals(w.getIsDefault()))
                        .isActive(Boolean.TRUE.equals(w.getIsActive()))
                        .status(Boolean.TRUE.equals(w.getIsActive()) ? "active" : "inactive")
                        .build())
                .toList();

        boolean active = Boolean.TRUE.equals(branch.getIsActive());
        return BranchDto.builder()
                .id(branch.getId())
                .code(branch.getCode())
                .name(branch.getName())
                .address(branch.getAddress())
                .phone(branch.getPhone())
                .email(branch.getEmail())
                .isMain(Boolean.TRUE.equals(branch.getIsMain()))
                .isActive(active)
                .status(active ? "active" : "inactive")
                .warehouses(warehouseDtos)
                .createdAt(branch.getCreatedAt())
                .updatedAt(branch.getUpdatedAt())
                .build();
    }
}
