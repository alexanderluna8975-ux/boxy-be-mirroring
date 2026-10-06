package com.boxy.boxy.modules.purchasing.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RejectPurchaseOrderRequest {
    @Size(max = 500)
    private String reason;
}
