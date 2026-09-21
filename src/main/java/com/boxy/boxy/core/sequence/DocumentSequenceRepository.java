package com.boxy.boxy.core.sequence;

import jakarta.persistence.LockModeType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DocumentSequence s WHERE s.company.id = :companyId AND s.documentType = :documentType")
    Optional<DocumentSequence> findForUpdate(@Param("companyId") Long companyId, @Param("documentType") DocumentType documentType);

    /** Unlocked read for non-reserving "what's next" previews — never call this to actually issue a folio. */
    @Query("SELECT s FROM DocumentSequence s WHERE s.company.id = :companyId AND s.documentType = :documentType")
    Optional<DocumentSequence> findByCompanyIdAndDocumentType(@Param("companyId") Long companyId, @Param("documentType") DocumentType documentType);

    /**
     * Locks the existing counter row for {@code companyId}+{@code documentType}, or creates it
     * at zero and locks that instead. Mirrors {@code StockLevelRepository.getOrCreateForUpdate}:
     * {@code SELECT ... FOR UPDATE} cannot lock a row that doesn't exist yet, so two concurrent
     * callers issuing the very first folio of a type can both take the "create" branch and
     * collide on {@code uk_document_sequence} — retried here as a same-row read instead of
     * surfacing a raw {@link DataIntegrityViolationException}.
     */
    default DocumentSequence getOrCreateForUpdate(com.boxy.boxy.modules.administration.entity.Company company, DocumentType documentType) {
        return findForUpdate(company.getId(), documentType)
                .orElseGet(() -> {
                    try {
                        return saveAndFlush(DocumentSequence.builder()
                                .company(company)
                                .documentType(documentType)
                                .lastValue(0L)
                                .build());
                    } catch (DataIntegrityViolationException raceLostToAnotherInsert) {
                        return findForUpdate(company.getId(), documentType)
                                .orElseThrow(() -> raceLostToAnotherInsert);
                    }
                });
    }
}
