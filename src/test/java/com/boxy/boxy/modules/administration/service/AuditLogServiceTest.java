package com.boxy.boxy.modules.administration.service;

import com.boxy.boxy.core.security.UserPrincipal;
import com.boxy.boxy.modules.administration.dto.AuditLogDto;
import com.boxy.boxy.modules.administration.entity.AuditLog;
import com.boxy.boxy.modules.administration.entity.User;
import com.boxy.boxy.modules.administration.repository.AuditLogRepository;
import com.boxy.boxy.modules.administration.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fase 3 — before this class existed, nothing ever wrote to `audit_logs`, so the Audit Logs
 * screen was always empty. These tests pin down that `record()` actually persists a row with
 * the actor/company/details it was given, that it never throws even when it can't (best-effort
 * by design — a logging bug must never break the mutation it's describing), and that
 * `getAuditLogs()` resolves a real display name instead of the old synthetic "Usuario #<id>".
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private UserRepository userRepository;

    private AuditLogService auditLogService;

    private static final Long COMPANY_ID = 1L;
    private static final Long USER_ID = 7L;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(auditLogRepository, userRepository, new ObjectMapper());

        UserPrincipal principal = UserPrincipal.create(USER_ID, COMPANY_ID, "tester", "tester@boxy.dev",
                "x", "Test User", 1L, "ACTIVE", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void record_persistsCompanyActorAndDetails() {
        when(auditLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        auditLogService.record("Usuario creado", "Usuario", "42", "Jane Doe", null, "correo: jane@boxy.dev");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getAction()).isEqualTo("Usuario creado");
        assertThat(saved.getResourceType()).isEqualTo("Usuario");
        assertThat(saved.getResourceId()).isEqualTo("42");
        assertThat(saved.getDetails()).contains("Jane Doe").contains("jane@boxy.dev");
    }

    @Test
    void record_neverThrowsWhenPersistenceFails() {
        when(auditLogRepository.save(any())).thenThrow(new RuntimeException("DB is down"));

        // Best-effort: a broken audit write must never bubble up into the caller's transaction.
        auditLogService.record("Usuario creado", "Usuario", "42", "Jane Doe", null, null);
    }

    @Test
    void record_neverThrowsWithNoAuthenticatedUser() {
        SecurityContextHolder.clearContext();

        auditLogService.record("Login fallido", "Sesión", null, "intento anónimo", null, null);

        verify(auditLogRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void getAuditLogs_resolvesRealActorNameInsteadOfSyntheticPlaceholder() {
        User author = User.builder().id(USER_ID).firstName("Jane").lastName("Doe").username("jane").build();
        AuditLog entry = AuditLog.builder()
                .id(1L)
                .companyId(COMPANY_ID)
                .userId(USER_ID)
                .action("Usuario creado")
                .resourceType("Usuario")
                .resourceId("42")
                .details("{\"entityLabel\":\"Jane Doe\",\"newValue\":\"correo: jane@boxy.dev\"}")
                .build();

        Pageable pageable = PageRequest.of(0, 20);
        when(auditLogRepository.search(eq(COMPANY_ID), eq(null), eq(null), any(), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(entry)));
        when(userRepository.findAllById(any())).thenReturn(List.of(author));

        Page<AuditLogDto> result = auditLogService.getAuditLogs(null, null, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        AuditLogDto dto = result.getContent().get(0);
        assertThat(dto.getActorName()).isEqualTo("Jane Doe");
        assertThat(dto.getEntityLabel()).isEqualTo("Jane Doe");
        assertThat(dto.getNewValue()).isEqualTo("correo: jane@boxy.dev");
    }

    @Test
    void getAuditLogs_fallsBackToPlaceholderWhenActorWasDeleted() {
        AuditLog entry = AuditLog.builder()
                .id(2L)
                .companyId(COMPANY_ID)
                .userId(999L)
                .action("Rol actualizado")
                .resourceType("Rol")
                .resourceId("5")
                .build();

        Pageable pageable = PageRequest.of(0, 20);
        when(auditLogRepository.search(eq(COMPANY_ID), eq(null), eq(null), any(), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(entry)));
        when(userRepository.findAllById(any())).thenReturn(List.of());

        Page<AuditLogDto> result = auditLogService.getAuditLogs(null, null, null, null, pageable);

        assertThat(result.getContent().get(0).getActorName()).isEqualTo("Usuario #999");
    }
}
