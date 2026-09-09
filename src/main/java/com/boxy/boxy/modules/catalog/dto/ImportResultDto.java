package com.boxy.boxy.modules.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportResultDto {
    private String filename;
    private String fileType; // "PRODUCTS", "CATALOG", "INVENTORY"
    private int totalRows;
    private int processedRows;
    private int createdCount;
    private int updatedCount;
    private int skippedCount;
    @Builder.Default
    private List<String> errors = new ArrayList<>();
    private String message;
}
