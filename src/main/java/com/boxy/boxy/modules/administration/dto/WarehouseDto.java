package com.boxy.boxy.modules.administration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseDto {
    private Long id;
    private String code;
    private String name;
    private Long branchId;
    private String branchName;
    private String branchCode;
    private String status;
    private boolean isDefault;
    private boolean isActive;
    private int productCount;
    private BigDecimal stockValue;
}
