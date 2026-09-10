package com.boxy.boxy.modules.administration.service;

import com.boxy.boxy.core.exception.ResourceNotFoundException;
import com.boxy.boxy.core.security.SecurityUtils;
import com.boxy.boxy.modules.administration.dto.CompanyProfileDto;
import com.boxy.boxy.modules.administration.dto.UpdateCompanyProfileRequest;
import com.boxy.boxy.modules.administration.entity.Company;
import com.boxy.boxy.modules.administration.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;

    @Transactional(readOnly = true)
    public CompanyProfileDto getCompanyProfile() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Company company = companyRepository.findByIdAndDeletedAtIsNull(companyId)
                .or(companyRepository::findFirstByDeletedAtIsNull)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        return toDto(company);
    }

    @Transactional
    public CompanyProfileDto updateCompanyProfile(UpdateCompanyProfileRequest request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Company company = companyRepository.findByIdAndDeletedAtIsNull(companyId)
                .or(companyRepository::findFirstByDeletedAtIsNull)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        if (request.getName() != null) company.setName(request.getName());
        if (request.getTradeName() != null) company.setTradeName(request.getTradeName());
        if (request.getSlogan() != null) company.setSlogan(request.getSlogan());
        if (request.getTaxId() != null) company.setTaxId(request.getTaxId());
        if (request.getEmail() != null) company.setEmail(request.getEmail());
        if (request.getPhone() != null) company.setPhone(request.getPhone());
        if (request.getAddress() != null) company.setAddress(request.getAddress());
        if (request.getLogoUrl() != null) company.setLogoUrl(request.getLogoUrl());

        if (request.getPrimaryColor() != null) company.setPrimaryColor(request.getPrimaryColor());
        if (request.getPrimaryHover() != null) company.setPrimaryHover(request.getPrimaryHover());
        if (request.getPrimarySubtleBg() != null) company.setPrimarySubtleBg(request.getPrimarySubtleBg());

        if (request.getCurrencyCode() != null) company.setCurrencyCode(request.getCurrencyCode());
        if (request.getCurrencySymbol() != null) company.setCurrencySymbol(request.getCurrencySymbol());
        if (request.getTaxName() != null) company.setTaxName(request.getTaxName());
        if (request.getDefaultTaxRate() != null) company.setDefaultTaxRate(request.getDefaultTaxRate());
        if (request.getTaxIdLabel() != null) company.setTaxIdLabel(request.getTaxIdLabel());
        if (request.getTimezone() != null) company.setTimezone(request.getTimezone());

        if (request.getHasPos() != null) company.setHasPos(request.getHasPos());
        if (request.getHasBatches() != null) company.setHasBatches(request.getHasBatches());
        if (request.getHasVariants() != null) company.setHasVariants(request.getHasVariants());
        if (request.getHasTransfers() != null) company.setHasTransfers(request.getHasTransfers());
        if (request.getHasPurchasing() != null) company.setHasPurchasing(request.getHasPurchasing());
        if (request.getHasQuotations() != null) company.setHasQuotations(request.getHasQuotations());
        if (request.getHasMultiBranch() != null) company.setHasMultiBranch(request.getHasMultiBranch());
        if (request.getUnitPrecision() != null) company.setUnitPrecision(request.getUnitPrecision());

        if (request.getTermProduct() != null) company.setTermProduct(request.getTermProduct());
        if (request.getTermProducts() != null) company.setTermProducts(request.getTermProducts());
        if (request.getTermInventory() != null) company.setTermInventory(request.getTermInventory());
        if (request.getTermCustomer() != null) company.setTermCustomer(request.getTermCustomer());
        if (request.getTermPos() != null) company.setTermPos(request.getTermPos());

        Company updated = companyRepository.save(company);
        return toDto(updated);
    }

    private CompanyProfileDto toDto(Company company) {
        return CompanyProfileDto.builder()
                .id(company.getId())
                .name(company.getName())
                .tradeName(company.getTradeName())
                .slogan(company.getSlogan())
                .taxId(company.getTaxId())
                .email(company.getEmail())
                .phone(company.getPhone())
                .address(company.getAddress())
                .logoUrl(company.getLogoUrl())
                .primaryColor(company.getPrimaryColor())
                .primaryHover(company.getPrimaryHover())
                .primarySubtleBg(company.getPrimarySubtleBg())
                .currencyCode(company.getCurrencyCode())
                .currencySymbol(company.getCurrencySymbol())
                .taxName(company.getTaxName())
                .defaultTaxRate(company.getDefaultTaxRate())
                .taxIdLabel(company.getTaxIdLabel())
                .timezone(company.getTimezone())
                .hasPos(company.getHasPos())
                .hasBatches(company.getHasBatches())
                .hasVariants(company.getHasVariants())
                .hasTransfers(company.getHasTransfers())
                .hasPurchasing(company.getHasPurchasing())
                .hasQuotations(company.getHasQuotations())
                .hasMultiBranch(company.getHasMultiBranch())
                .unitPrecision(company.getUnitPrecision())
                .termProduct(company.getTermProduct())
                .termProducts(company.getTermProducts())
                .termInventory(company.getTermInventory())
                .termCustomer(company.getTermCustomer())
                .termPos(company.getTermPos())
                .build();
    }
}
