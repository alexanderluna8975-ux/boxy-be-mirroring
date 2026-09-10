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
public class CategoryDto {
    private Long id;
    private Long parentId;
    private String code;

    @NotBlank(message = "Name is required")
    private String name;

    private String description;
    private boolean isActive;

    /** How many non-deleted products currently use this category — the delete guard checks this too. */
    private long productCount;
}
