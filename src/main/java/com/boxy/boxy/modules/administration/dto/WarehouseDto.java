package com.boxy.boxy.modules.administration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    // See BranchDto for why @JsonProperty is needed on Lombok-generated isX() getters.
    @JsonProperty("isDefault")
    private boolean isDefault;
    @JsonProperty("isActive")
    private boolean isActive;

    private int productCount;
    private BigDecimal stockValue;
}
