package com.boxy.boxy.core.sequence;

/**
 * One correlative counter per (company, type) — the source of truth for every sequential
 * document folio the platform issues (ADJ-00001, TRF-00001, COT-00001, PR-00001, PO-00001,
 * REC-00001, and the numeric part of an invoice's series-number folio).
 * <p>
 * Adding a new document type is a one-line addition here; nothing else needs to change.
 */
public enum DocumentType {
    ADJUSTMENT("ADJ-"),
    TRANSFER("TRF-"),
    /** Invoice folios are {@code series + "-" + number} (e.g. {@code B001-00042}); the series,
     *  not this counter, carries the prefix, so {@link #prefix()} is unused for this type — see
     *  {@link DocumentSequenceService#next(Long, DocumentType)}. */
    SALE(null),
    QUOTATION("COT-"),
    PRICE_ADJUSTMENT("PR-"),
    PURCHASE_ORDER("PO-"),
    GOODS_RECEIPT("REC-");

    private final String prefix;

    DocumentType(String prefix) {
        this.prefix = prefix;
    }

    /** Folio prefix, or {@code null} for types (like {@link #SALE}) that format their own folio. */
    public String prefix() {
        return prefix;
    }
}
