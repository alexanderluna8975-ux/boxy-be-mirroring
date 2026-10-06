package com.boxy.boxy.core.realtime.events;

public record SaleEvent(Type type, Long companyId, Long branchId, Long saleId, String saleNumber, Long actorId) {

    public enum Type { CREATED, VOIDED, PAYMENT_RECORDED }
}
