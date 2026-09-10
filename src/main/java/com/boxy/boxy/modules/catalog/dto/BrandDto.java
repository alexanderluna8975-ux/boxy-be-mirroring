package com.boxy.boxy.modules.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandDto {
    private Long id;

    @NotBlank(message = "Name is required")
    private String name;

    private String description;
    private boolean isActive;

    /** How many non-deleted products currently use this brand — the delete guard checks this too. */
    private long productCount;
}
