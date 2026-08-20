package com.boxy.boxy.modules.administration.service;

import com.boxy.boxy.core.security.SecurityUtils;
import com.boxy.boxy.modules.administration.dto.AuditLogDto;
import com.boxy.boxy.modules.administration.dto.TaxDto;
import com.boxy.boxy.modules.administration.entity.Tax;
import com.boxy.boxy.modules.administration.repository.AuditLogRepository;
import com.boxy.boxy.modules.administration.repository.TaxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaxService {

    private final TaxRepository taxRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public List<TaxDto> getAllTaxes() {
        String companyId = SecurityUtils.getCurrentCompanyId();
        return taxRepository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
                .map(t -> TaxDto.builder()
                        .id(t.getId())
                        .name(t.getName())
                        .rate(t.getRate())
                        .isDefault(Boolean.TRUE.equals(t.getIsDefault()))
                        .isActive(Boolean.TRUE.equals(t.getIsActive()))
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> getAuditLogs(Pageable pageable) {
        String companyId = SecurityUtils.getCurrentCompanyId();
        return auditLogRepository.findByCompanyIdOrderByCreatedAtDesc(companyId, pageable)
                .map(log -> AuditLogDto.builder()
                        .id(log.getId())
                        .userId(log.getUserId())
                        .action(log.getAction())
                        .resourceType(log.getResourceType())
                        .resourceId(log.getResourceId())
                        .ipAddress(log.getIpAddress())
                        .userAgent(log.getUserAgent())
                        .details(log.getDetails())
                        .createdAt(log.getCreatedAt())
                        .build());
    }
}
