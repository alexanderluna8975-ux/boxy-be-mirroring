package com.boxy.boxy.modules.notifications.controller;

import com.boxy.boxy.modules.notifications.dto.NotificationDto;
import com.boxy.boxy.modules.notifications.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock private NotificationService notificationService;
    @InjectMocks private NotificationController controller;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private static NotificationDto dto(long id) {
        return NotificationDto.builder().id(id).type("LOW_STOCK").severity("warning").title("Stock bajo")
                .message("Quedan 3.").read(false).createdAt("2026-01-01T10:00:00Z").build();
    }

    @Test
    void listReturnsThePageWithItsMeta() throws Exception {
        when(notificationService.list(anyBoolean(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(dto(1), dto(2))));

        mvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].title").value("Stock bajo"))
                .andExpect(jsonPath("$.meta.page").value(1));
    }

    @Test
    void thePageSizeIsCappedSoOneRequestCannotPullTheWholeTable() throws Exception {
        when(notificationService.list(anyBoolean(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/notifications").param("pageSize", "100000").param("page", "0"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationService).list(eq(false), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getPageNumber()).isZero(); // a bad page number is clamped to the first
    }

    @Test
    void unreadOnlyIsForwarded() throws Exception {
        when(notificationService.list(anyBoolean(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/notifications").param("unreadOnly", "true")).andExpect(status().isOk());

        verify(notificationService).list(eq(true), any(Pageable.class));
    }

    @Test
    void unreadCountIsWrappedInAnObject() throws Exception {
        when(notificationService.unreadCount()).thenReturn(3L);

        mvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(3));
    }

    @Test
    void markReadReturnsTheUpdatedNotification() throws Exception {
        when(notificationService.markRead(5L)).thenReturn(NotificationDto.builder().id(5L).read(true).build());

        mvc.perform(patch("/api/v1/notifications/5/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));
    }

    @Test
    void markAllReadReportsHowManyWereUpdated() throws Exception {
        when(notificationService.markAllRead()).thenReturn(4);

        mvc.perform(patch("/api/v1/notifications/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(4));
    }
}
