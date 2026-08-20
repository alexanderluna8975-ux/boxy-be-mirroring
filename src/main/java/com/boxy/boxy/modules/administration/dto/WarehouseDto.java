package com.boxy.boxy.modules.administration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseDto {
    private String id;
    private String code;
    private String name;
    private boolean isDefault;
    private boolean isActive;
}
