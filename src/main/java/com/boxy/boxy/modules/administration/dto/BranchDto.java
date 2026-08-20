package com.boxy.boxy.modules.administration.dto;

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
public class BranchDto {
    private String id;
    private String code;
    private String name;
    private String address;
    private String phone;
    private String email;
    private boolean isMain;
    private boolean isActive;
    private List<WarehouseDto> warehouses;
    private Instant createdAt;
}
