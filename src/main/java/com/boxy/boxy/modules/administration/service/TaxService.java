package com.boxy.boxy.modules.administration.service;

import com.boxy.boxy.core.exception.ResourceNotFoundException;
import com.boxy.boxy.core.security.SecurityUtils;
import com.boxy.boxy.modules.administration.dto.TaxDto;
import com.boxy.boxy.modules.administration.entity.Company;
import com.boxy.boxy.modules.administration.entity.Tax;
import com.boxy.boxy.modules.administration.repository.CompanyRepository;
import com.boxy.boxy.modules.administration.repository.TaxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaxService {

    private final TaxRepository taxRepository;
    private final CompanyRepository companyRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<TaxDto> getAllTaxes() {
        Long companyId = SecurityUtils.requireCurrentCompanyId();
        return taxRepository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public TaxDto createTax(TaxDto request) {
        Long companyId = SecurityUtils.requireCurrentCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        if (request.isDefault()) {
            clearExistingDefault(companyId);
        }

        Tax tax = Tax.builder()
                .company(company)
                .name(request.getName())
                .rate(request.getRate() != null ? request.getRate() : BigDecimal.ZERO)
                .isDefault(request.isDefault())
                .isActive(true)
                .build();

        Tax saved = taxRepository.save(tax);
        auditLogService.record("Impuesto creado", "Impuesto", String.valueOf(saved.getId()), saved.getName(),
                null, "tasa: " + saved.getRate() + (saved.getIsDefault() ? " (predeterminado)" : ""));
        return toDto(saved);
    }

    @Transactional
    public TaxDto updateTax(Long id, TaxDto request) {
        Long companyId = SecurityUtils.requireCurrentCompanyId();
        Tax tax = findOwnedTax(id);
        String previous = describeTax(tax);

        if (request.getName() != null) tax.setName(request.getName());
        if (request.getRate() != null) tax.setRate(request.getRate());
        if (request.isDefault() && !Boolean.TRUE.equals(tax.getIsDefault())) {
            clearExistingDefault(companyId);
        }
        tax.setIsDefault(request.isDefault());

        Tax saved = taxRepository.save(tax);
        auditLogService.record("Impuesto actualizado", "Impuesto", String.valueOf(saved.getId()), saved.getName(),
                previous, describeTax(saved));
        return toDto(saved);
    }

    @Transactional
    public TaxDto deactivateTax(Long id) {
        Tax tax = findOwnedTax(id);
        boolean wasActive = Boolean.TRUE.equals(tax.getIsActive());
        tax.setIsActive(!wasActive);
        Tax saved = taxRepository.save(tax);
        auditLogService.record(wasActive ? "Impuesto desactivado" : "Impuesto activado", "Impuesto",
                String.valueOf(saved.getId()), saved.getName(),
                wasActive ? "active" : "inactive", wasActive ? "inactive" : "active");
        return toDto(saved);
    }

    /** At most one default tax per company — the FK/DDL don't enforce this, so the service does. */
    private void clearExistingDefault(Long companyId) {
        taxRepository.findByCompanyIdAndIsDefaultTrueAndDeletedAtIsNull(companyId)
                .ifPresent(existingDefault -> {
                    existingDefault.setIsDefault(false);
                    taxRepository.save(existingDefault);
                });
    }

    /** 404s (not 403) on a tax belonging to another company — same treatment as "doesn't exist". */
    private Tax findOwnedTax(Long id) {
        Long companyId = SecurityUtils.requireCurrentCompanyId();
        return taxRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Tax", id));
    }

    private String describeTax(Tax tax) {
        return "nombre: " + tax.getName() + "\ntasa: " + tax.getRate()
                + "\npredeterminado: " + Boolean.TRUE.equals(tax.getIsDefault());
    }

    private TaxDto toDto(Tax t) {
        boolean active = Boolean.TRUE.equals(t.getIsActive());
        return TaxDto.builder()
                .id(t.getId())
                .name(t.getName())
                .rate(t.getRate())
                .isDefault(Boolean.TRUE.equals(t.getIsDefault()))
                .isActive(active)
                .status(active ? "active" : "inactive")
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}
