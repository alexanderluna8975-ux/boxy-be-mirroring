package com.boxy.boxy.modules.sales.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** SKUs / barcodes pasted into POS's "Pegar lista"; capped so one request can't ask for the whole catalog. */
@Data
public class CatalogLookupRequest {

    @NotNull
    @Size(max = 1000, message = "At most 1000 codes per lookup")
    private List<String> codes;
}
