package com.boxy.boxy.core.sequence;

import com.boxy.boxy.core.exception.ResourceNotFoundException;
import com.boxy.boxy.modules.administration.entity.Company;
import com.boxy.boxy.modules.administration.repository.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentSequenceServiceTest {

    // CALLS_REAL_METHODS so DocumentSequenceRepository's default getOrCreateForUpdate(...) runs
    // for real against the mocked findForUpdate/saveAndFlush — same pattern as
    // StockAdjustmentServiceTest does for StockLevelRepository.getOrCreateForUpdate.
    @Mock(answer = Answers.CALLS_REAL_METHODS)
    private DocumentSequenceRepository documentSequenceRepository;
    @Mock
    private CompanyRepository companyRepository;

    @InjectMocks
    private DocumentSequenceService service;

    private static final Long COMPANY_ID = 1L;
    private Company company;

    @BeforeEach
    void setUp() {
        company = Company.builder().id(COMPANY_ID).name("Acme").taxId("TAX-1").build();
    }

    @Test
    void next_startsAtOneForABrandNewCounter() {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(documentSequenceRepository.findForUpdate(COMPANY_ID, DocumentType.ADJUSTMENT)).thenReturn(Optional.empty());
        when(documentSequenceRepository.saveAndFlush(any(DocumentSequence.class))).thenAnswer(inv -> inv.getArgument(0));
        when(documentSequenceRepository.save(any(DocumentSequence.class))).thenAnswer(inv -> inv.getArgument(0));

        long value = service.next(COMPANY_ID, DocumentType.ADJUSTMENT);

        assertThat(value).isEqualTo(1L);
    }

    @Test
    void next_isCorrelative_incrementsByExactlyOnePerCall() {
        DocumentSequence sequence = DocumentSequence.builder().id(9L).company(company)
                .documentType(DocumentType.ADJUSTMENT).lastValue(4L).build();
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(documentSequenceRepository.findForUpdate(COMPANY_ID, DocumentType.ADJUSTMENT)).thenReturn(Optional.of(sequence));
        when(documentSequenceRepository.save(any(DocumentSequence.class))).thenAnswer(inv -> inv.getArgument(0));

        long value = service.next(COMPANY_ID, DocumentType.ADJUSTMENT);

        assertThat(value).isEqualTo(5L);
        assertThat(sequence.getLastValue()).isEqualTo(5L);
    }

    @Test
    void next_doesNotLeakIntoADifferentDocumentTypesCounter() {
        // Two independent rows for the same company: creating an adjustment must never touch
        // the transfer counter, matching the plan's "independent per type, but correlative" scope.
        DocumentSequence adjustmentSeq = DocumentSequence.builder().id(1L).company(company)
                .documentType(DocumentType.ADJUSTMENT).lastValue(10L).build();
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(documentSequenceRepository.findForUpdate(COMPANY_ID, DocumentType.ADJUSTMENT)).thenReturn(Optional.of(adjustmentSeq));
        when(documentSequenceRepository.save(any(DocumentSequence.class))).thenAnswer(inv -> inv.getArgument(0));

        service.next(COMPANY_ID, DocumentType.ADJUSTMENT);

        verify(documentSequenceRepository, never()).findForUpdate(any(), org.mockito.ArgumentMatchers.eq(DocumentType.TRANSFER));
    }

    @Test
    void next_throwsWhenCompanyDoesNotExist() {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.next(COMPANY_ID, DocumentType.ADJUSTMENT))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void nextFolio_formatsWithPrefixAndFiveDigits() {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(documentSequenceRepository.findForUpdate(COMPANY_ID, DocumentType.ADJUSTMENT)).thenReturn(Optional.empty());
        when(documentSequenceRepository.saveAndFlush(any(DocumentSequence.class))).thenAnswer(inv -> inv.getArgument(0));
        when(documentSequenceRepository.save(any(DocumentSequence.class))).thenAnswer(inv -> inv.getArgument(0));

        String folio = service.nextFolio(COMPANY_ID, DocumentType.ADJUSTMENT);

        assertThat(folio).isEqualTo("ADJ-00001");
    }

    @Test
    void nextFolio_padsToFiveDigitsEvenPastTenThousand() {
        DocumentSequence sequence = DocumentSequence.builder().id(9L).company(company)
                .documentType(DocumentType.TRANSFER).lastValue(99998L).build();
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(documentSequenceRepository.findForUpdate(COMPANY_ID, DocumentType.TRANSFER)).thenReturn(Optional.of(sequence));
        when(documentSequenceRepository.save(any(DocumentSequence.class))).thenAnswer(inv -> inv.getArgument(0));

        String folio = service.nextFolio(COMPANY_ID, DocumentType.TRANSFER);

        assertThat(folio).isEqualTo("TRF-99999");
    }

    @Test
    void nextFolio_rejectsSaleBecauseItHasNoOwnPrefix() {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(documentSequenceRepository.findForUpdate(COMPANY_ID, DocumentType.SALE)).thenReturn(Optional.empty());
        when(documentSequenceRepository.saveAndFlush(any(DocumentSequence.class))).thenAnswer(inv -> inv.getArgument(0));
        when(documentSequenceRepository.save(any(DocumentSequence.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.nextFolio(COMPANY_ID, DocumentType.SALE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- peekNext / peekNextFolio (non-reserving preview) ----

    @Test
    void peekNext_returnsCurrentPlusOne_withoutAnyRowExistingYet() {
        when(documentSequenceRepository.findByCompanyIdAndDocumentType(COMPANY_ID, DocumentType.QUOTATION))
                .thenReturn(Optional.empty());

        assertThat(service.peekNext(COMPANY_ID, DocumentType.QUOTATION)).isEqualTo(1L);
    }

    @Test
    void peekNext_doesNotMutateState() {
        when(documentSequenceRepository.findByCompanyIdAndDocumentType(COMPANY_ID, DocumentType.QUOTATION))
                .thenReturn(Optional.empty());

        service.peekNext(COMPANY_ID, DocumentType.QUOTATION);
        service.peekNext(COMPANY_ID, DocumentType.QUOTATION);

        // Calling twice returns the same preview — nothing was saved/incremented.
        verify(documentSequenceRepository, never()).save(any());
        verify(documentSequenceRepository, never()).findForUpdate(any(), any());
    }

    @Test
    void peekNextFolio_matchesWhatNextFolioWouldActuallyIssue() {
        DocumentSequence sequence = DocumentSequence.builder().id(9L).company(company)
                .documentType(DocumentType.QUOTATION).lastValue(41L).build();
        when(documentSequenceRepository.findByCompanyIdAndDocumentType(COMPANY_ID, DocumentType.QUOTATION))
                .thenReturn(Optional.of(sequence));

        assertThat(service.peekNextFolio(COMPANY_ID, DocumentType.QUOTATION)).isEqualTo("COT-00042");
    }
}
