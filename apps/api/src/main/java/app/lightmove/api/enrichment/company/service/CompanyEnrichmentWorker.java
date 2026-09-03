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
 * Researches a plugin-captured company, off the capture's thread and after its commit — the same
 * shape as {@code CandidateEnrichmentWorker}, for the same reasons: {@code AFTER_COMMIT} because the
 * write updates a row this listener must be able to see, {@code @Async} because the vendor call takes
 * seconds, the write crossing back into {@link TriageCompanyService#applyEnrichment} so no database
 * connection is held across it, and failures swallowed because a capture broken by its own enrichment
 * is a bug report.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class CompanyEnrichmentWorker {

    private final LinkedInCompanyEnricher enricher;
    private final TriageCompanyService companies;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void enrich(TriageCompanyCapturedEvent event) {
        try {
            enricher.fetch(event.linkedinSlug()).ifPresentOrElse(
                    details -> companies.applyEnrichment(event.projectId(), event.companyId(), details),
                    () -> log.info("No research found for company {}", event.companyId()));
        } catch (RuntimeException ex) {
            log.error("Failed to enrich company {}", event.companyId(), ex);
        }
    }
}
