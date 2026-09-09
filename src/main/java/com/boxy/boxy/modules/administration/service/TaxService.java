package com.boxy.boxy.modules.administration.service;

import com.boxy.boxy.core.exception.ResourceNotFoundException;
import com.boxy.boxy.core.security.SecurityUtils;
import com.boxy.boxy.modules.administration.dto.AuditLogDto;
import com.boxy.boxy.modules.administration.dto.TaxDto;
import com.boxy.boxy.modules.administration.entity.Company;
import com.boxy.boxy.modules.administration.entity.Tax;
import com.boxy.boxy.modules.administration.repository.AuditLogRepository;
import com.boxy.boxy.modules.administration.repository.CompanyRepository;
import com.boxy.boxy.modules.administration.repository.TaxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaxService {

    private final TaxRepository taxRepository;
    private final AuditLogRepository auditLogRepository;
    private final CompanyRepository companyRepository;

    @Transactional(readOnly = true)
    public List<TaxDto> getAllTaxes() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return taxRepository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public TaxDto createTax(TaxDto request) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        Tax tax = Tax.builder()
                .company(company)
                .name(request.getName())
                .rate(request.getRate() != null ? request.getRate() : BigDecimal.ZERO)
                .isDefault(request.isDefault())
                .isActive(true)
                .build();

        return toDto(taxRepository.save(tax));
    }

    @Transactional
    public TaxDto updateTax(Long id, TaxDto request) {
        Tax tax = taxRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tax", id));

        if (request.getName() != null) tax.setName(request.getName());
        if (request.getRate() != null) tax.setRate(request.getRate());
        tax.setIsDefault(request.isDefault());

        return toDto(taxRepository.save(tax));
    }

    @Transactional
    public TaxDto deactivateTax(Long id) {
        Tax tax = taxRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tax", id));
        tax.setIsActive(!Boolean.TRUE.equals(tax.getIsActive()));
        return toDto(taxRepository.save(tax));
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> getAuditLogs(Pageable pageable) {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return auditLogRepository.findByCompanyIdOrderByCreatedAtDesc(companyId, pageable)
                .map(log -> AuditLogDto.builder()
                        .id(log.getId())
                        .userId(log.getUserId())
                        .actorName(log.getUserId() != null ? "Usuario #" + log.getUserId() : "Super Admin")
                        .action(log.getAction())
                        .entity(log.getResourceType())
                        .resourceType(log.getResourceType())
                        .resourceId(log.getResourceId())
                        .entityLabel(log.getResourceType() != null ? log.getResourceType() + " #" + (log.getResourceId() != null ? log.getResourceId() : "") : "Registro")
                        .ipAddress(log.getIpAddress())
                        .userAgent(log.getUserAgent())
                        .details(log.getDetails())
                        .createdAt(log.getCreatedAt())
                        .occurredAt(log.getCreatedAt() != null ? log.getCreatedAt().toString() : java.time.Instant.now().toString())
                        .build());
    }

    private TaxDto toDto(Tax t) {
        return TaxDto.builder()
                .id(t.getId())
                .name(t.getName())
                .rate(t.getRate())
                .isDefault(Boolean.TRUE.equals(t.getIsDefault()))
                .isActive(Boolean.TRUE.equals(t.getIsActive()))
                .build();
    }
}
