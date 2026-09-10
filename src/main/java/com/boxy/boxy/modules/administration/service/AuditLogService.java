package com.boxy.boxy.modules.administration.service;

import com.boxy.boxy.core.security.SecurityUtils;
import com.boxy.boxy.core.security.UserPrincipal;
import com.boxy.boxy.core.web.DateFilterParser;
import com.boxy.boxy.modules.administration.dto.AuditLogDto;
import com.boxy.boxy.modules.administration.entity.AuditLog;
import com.boxy.boxy.modules.administration.entity.User;
import com.boxy.boxy.modules.administration.repository.AuditLogRepository;
import com.boxy.boxy.modules.administration.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The one place that writes to {@code audit_logs}. Nothing wrote to it before this class
 * existed (Fase 3 of the administration plan) — the Audit Logs screen always returned empty.
 *
 * {@link #record} is deliberately best-effort: a bug here must never roll back or fail the
 * business mutation it's describing, so every exception is caught and logged instead of
 * propagated.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /**
     * @param action       short past-tense description, e.g. "Usuario creado" — matches the
     *                     FE's {@code AuditLogEntry.action} display convention.
     * @param entityType   the FE's fixed entity vocabulary, e.g. "Usuario", "Rol", "Sucursal" —
     *                     stored verbatim so the FE's entity filter can match it directly.
     * @param resourceId   the affected record's id, as a string (nullable for actions with no
     *                     single resource, e.g. a bulk operation).
     * @param entityLabel  the record's display name at the time, e.g. a branch name.
     * @param previousValue nullable "before" snapshot, free text.
     * @param newValue     nullable "after" snapshot, free text.
     */
    @Transactional
    public void record(String action, String entityType, String resourceId, String entityLabel,
                        String previousValue, String newValue) {
        try {
            Long companyId = SecurityUtils.requireCurrentCompanyId();
            Long userId = SecurityUtils.getCurrentUser().map(UserPrincipal::getId).orElse(null);
            HttpServletRequest request = currentRequest();

            Map<String, String> details = new LinkedHashMap<>();
            details.put("entityLabel", entityLabel);
            if (previousValue != null) {
                details.put("previousValue", previousValue);
            }
            if (newValue != null) {
                details.put("newValue", newValue);
            }

            AuditLog entry = AuditLog.builder()
                    .companyId(companyId)
                    .userId(userId)
                    .action(action)
                    .resourceType(entityType)
                    .resourceId(resourceId)
                    .ipAddress(request != null ? clientIp(request) : null)
                    .userAgent(request != null ? request.getHeader("User-Agent") : null)
                    .details(objectMapper.writeValueAsString(details))
                    .build();

            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to record audit log: action='{}' entityType='{}' resourceId='{}'",
                    action, entityType, resourceId, e);
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> getAuditLogs(String search, String entity, String dateFrom, String dateTo, Pageable pageable) {
        Long companyId = SecurityUtils.requireCurrentCompanyId();
        Page<AuditLog> page = auditLogRepository.search(
                companyId, blankToNull(search), blankToNull(entity),
                DateFilterParser.parseStart(dateFrom), DateFilterParser.parseEnd(dateTo), pageable);

        Map<Long, String> actorNames = resolveActorNames(page.getContent());
        return page.map(entry -> toDto(entry, actorNames));
    }

    private Map<Long, String> resolveActorNames(List<AuditLog> entries) {
        Set<Long> userIds = entries.stream()
                .map(AuditLog::getUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, this::displayName));
    }

    private String displayName(User user) {
        String fullName = String.format("%s %s", user.getFirstName(), user.getLastName()).trim();
        return !fullName.isBlank() ? fullName : user.getUsername();
    }

    private AuditLogDto toDto(AuditLog entry, Map<Long, String> actorNames) {
        Map<String, String> details = parseDetails(entry.getDetails());
        String fallbackLabel = entry.getResourceType() != null
                ? entry.getResourceType() + (entry.getResourceId() != null ? " #" + entry.getResourceId() : "")
                : "Registro";

        return AuditLogDto.builder()
                .id(entry.getId())
                .userId(entry.getUserId())
                .actorName(entry.getUserId() != null
                        ? actorNames.getOrDefault(entry.getUserId(), "Usuario #" + entry.getUserId())
                        : "Sistema")
                .action(entry.getAction())
                .entity(entry.getResourceType())
                .resourceType(entry.getResourceType())
                .resourceId(entry.getResourceId())
                .entityLabel(details.getOrDefault("entityLabel", fallbackLabel))
                .previousValue(details.get("previousValue"))
                .newValue(details.get("newValue"))
                .ipAddress(entry.getIpAddress())
                .userAgent(entry.getUserAgent())
                .details(entry.getDetails())
                .createdAt(entry.getCreatedAt())
                .occurredAt(entry.getCreatedAt() != null ? entry.getCreatedAt().toString() : null)
                .build();
    }

    private Map<String, String> parseDetails(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rawJson, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private HttpServletRequest currentRequest() {
        try {
            return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        } catch (Exception e) {
            return null;
        }
    }

    /** Honors a reverse proxy's `X-Forwarded-For` (first hop) before falling back to the socket address. */
    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
