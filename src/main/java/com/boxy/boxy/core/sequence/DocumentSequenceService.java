package com.boxy.boxy.core.sequence;

import com.boxy.boxy.core.exception.ResourceNotFoundException;
import com.boxy.boxy.modules.administration.entity.Company;
import com.boxy.boxy.modules.administration.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single source of correlative document folios (ADJ-00001, TRF-00001, COT-00001, PR-00001,
 * PO-00001, REC-00001, plus the numeric part of an invoice's {@code series-number}), replacing
 * the per-service ad hoc generation this codebase used to have — a mix of
 * {@code UUID.randomUUID()} fragments (adjustments, transfers) and {@code System.currentTimeMillis()}
 * (sales, quotations, purchase orders, goods receipts), neither of which is sequential, plus a
 * plain {@code count() + 1} for price adjustments that is not safe under concurrent inserts.
 * <p>
 * Every document type shares this one mechanism but keeps its own independent counter, scoped
 * per company — creating an Adjustment never advances the Transfer counter, matching how the
 * numbering worked (in scope, if not in safety) before this change.
 */
@Service
@RequiredArgsConstructor
public class DocumentSequenceService {

    private static final String NUMBER_FORMAT = "%05d";

    private final DocumentSequenceRepository documentSequenceRepository;
    private final CompanyRepository companyRepository;

    /**
     * Atomically advances the counter for {@code companyId}+{@code documentType} and returns the
     * new value. Runs inside the caller's transaction (not {@code REQUIRES_NEW}): if the calling
     * use case rolls back, the reservation rolls back with it rather than leaving a permanent gap.
     * <p>
     * Use this directly (rather than {@link #nextFolio}) for {@link DocumentType#SALE}, whose
     * folio is {@code series + "-" + number} — the caller owns the series and only needs the
     * zero-padded number.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public long next(Long companyId, DocumentType documentType) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));
        DocumentSequence sequence = documentSequenceRepository.getOrCreateForUpdate(company, documentType);
        sequence.setLastValue(sequence.getLastValue() + 1);
        documentSequenceRepository.save(sequence);
        return sequence.getLastValue();
    }

    /** {@link #next(Long, DocumentType)} formatted as {@code <prefix><5-digit number>}, e.g. {@code ADJ-00001}. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextFolio(Long companyId, DocumentType documentType) {
        return formatFolio(documentType, next(companyId, documentType));
    }

    /**
     * Non-reserving preview of what {@link #next} would return right now, unformatted — for
     * {@link DocumentType#SALE}, whose folio the caller formats itself (see {@link #next}), or
     * anywhere else the raw number is more useful than a folio string. Never treat the result as
     * reserved: two previews in a row return the same value until one of them calls {@code next*}.
     */
    @Transactional(readOnly = true)
    public long peekNext(Long companyId, DocumentType documentType) {
        return documentSequenceRepository.findByCompanyIdAndDocumentType(companyId, documentType)
                .map(DocumentSequence::getLastValue)
                .orElse(0L) + 1;
    }

    /** {@link #peekNext(Long, DocumentType)} formatted as {@code <prefix><5-digit number>}. */
    @Transactional(readOnly = true)
    public String peekNextFolio(Long companyId, DocumentType documentType) {
        return formatFolio(documentType, peekNext(companyId, documentType));
    }

    private static String formatFolio(DocumentType documentType, long value) {
        String prefix = documentType.prefix();
        if (prefix == null) {
            throw new IllegalArgumentException(
                    documentType + " has no folio prefix of its own — format its folio at the call site (see SALE).");
        }
        return prefix + String.format(NUMBER_FORMAT, value);
    }
}
