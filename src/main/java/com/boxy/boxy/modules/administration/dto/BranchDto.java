package com.boxy.boxy.modules.administration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    private Long id;
    private String code;
    private String name;
    private String address;
    private String phone;
    private String email;

    // Lombok generates isMain()/isActive() for these primitive booleans, and Jackson
    // (LOWER_CAMEL_CASE) publishes an "isX" getter's property as "x" — i.e. "main"/"active",
    // not "isMain"/"isActive". @JsonProperty pins the wire name so the FE's isMain/isActive
    // readers actually get populated instead of silently seeing every branch as inactive.
    @JsonProperty("isMain")
    private boolean isMain;
    @JsonProperty("isActive")
    private boolean isActive;
    private String status;
    private List<WarehouseDto> warehouses;
    private Instant createdAt;
    private Instant updatedAt;
}
