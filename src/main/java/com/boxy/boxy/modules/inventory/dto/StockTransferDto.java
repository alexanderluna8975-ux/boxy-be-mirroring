package com.boxy.boxy.modules.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTransferDto {
    private String id;
    private String transferNumber;
    private String sourceWarehouseId;
    private String sourceWarehouseName;
    private String destinationWarehouseId;
    private String destinationWarehouseName;
    private String status;
    private String notes;
    private String requestedByName;
    private Instant dispatchedAt;
    private Instant receivedAt;
    private List<StockTransferItemDto> items;
    private Instant createdAt;
}
