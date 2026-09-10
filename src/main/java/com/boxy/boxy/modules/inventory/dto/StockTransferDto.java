package com.boxy.boxy.modules.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTransferDto {
    private Long id;
    private String transferNumber;
    private String folio;
    private Long sourceWarehouseId;
    private Long originWarehouseId;
    private String sourceWarehouseName;
    private String originWarehouseName;
    private String sourceWarehouseCode;
    private String originWarehouseCode;
    private Long destinationWarehouseId;
    private String destinationWarehouseName;
    private String destinationWarehouseCode;
    private String status;
    private String notes;
    private String requestedByName;
    private String requestedBy;
    private Instant requestedAt;
    private String approvedBy;
    private Instant approvedAt;
    private Instant dispatchedAt;
    private Instant shippedAt;
    private Instant receivedAt;
    private List<StockTransferItemDto> items;
    private List<StockTransferItemDto> lines;
    private int lineCount;
    private Instant createdAt;
}
