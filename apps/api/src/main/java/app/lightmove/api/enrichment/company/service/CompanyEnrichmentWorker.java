package app.lightmove.api.enrichment.company.service;

import app.lightmove.api.triagecompany.model.TriageCompanyCapturedEvent;
import app.lightmove.api.triagecompany.service.TriageCompanyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Researches a plugin-captured company after its commit, off the capture's thread — the same shape
 * as {@code CandidateEnrichmentWorker}, for its reasons.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class CompanyEnrichmentWorker {

    private final CompanyResearch research;
    private final TriageCompanyService companies;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void enrich(TriageCompanyCapturedEvent event) {
        try {
            research.of(event.linkedinSlug()).ifPresentOrElse(
                    details -> companies.applyEnrichment(event.projectId(), event.companyId(), details),
                    () -> log.info("No research found for company {}", event.companyId()));
        } catch (RuntimeException ex) {
            log.error("Failed to enrich company {}", event.companyId(), ex);
        }
    }
}
