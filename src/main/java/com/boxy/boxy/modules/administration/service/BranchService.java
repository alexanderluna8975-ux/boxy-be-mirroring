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

    @Transactional(readOnly = true)
    public List<BranchDto> getAllBranches() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return branchRepository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public BranchDto getBranchById(Long id) {
        Branch branch = branchRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", id));
        return toDto(branch);
    }

    @Transactional
    public BranchDto createBranch(CreateBranchRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        if (branchRepository.findByCompanyIdAndCodeAndDeletedAtIsNull(companyId, request.getCode()).isPresent()) {
            throw new BusinessException("BRANCH_CODE_EXISTS", "A branch with code '" + request.getCode() + "' already exists.");
        }

        Branch branch = Branch.builder()
                .company(company)
                .code(request.getCode().toUpperCase())
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

        return toDto(savedBranch);
    }

    @Transactional
    public BranchDto updateBranch(Long id, CreateBranchRequest request) {
        Branch branch = branchRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", id));

        if (request.getName() != null) branch.setName(request.getName());
        if (request.getCode() != null) branch.setCode(request.getCode().toUpperCase());
        if (request.getAddress() != null) branch.setAddress(request.getAddress());
        if (request.getPhone() != null) branch.setPhone(request.getPhone());
        if (request.getEmail() != null) branch.setEmail(request.getEmail());

        return toDto(branchRepository.save(branch));
    }

    @Transactional
    public BranchDto deactivateBranch(Long id) {
        Branch branch = branchRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", id));
        branch.setIsActive(!Boolean.TRUE.equals(branch.getIsActive()));
        return toDto(branchRepository.save(branch));
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

        return BranchDto.builder()
                .id(branch.getId())
                .code(branch.getCode())
                .name(branch.getName())
                .address(branch.getAddress())
                .phone(branch.getPhone())
                .email(branch.getEmail())
                .isMain(Boolean.TRUE.equals(branch.getIsMain()))
                .isActive(Boolean.TRUE.equals(branch.getIsActive()))
                .status(Boolean.TRUE.equals(branch.getIsActive()) ? "active" : "inactive")
                .warehouses(warehouseDtos)
                .createdAt(branch.getCreatedAt())
                .build();
    }
}
