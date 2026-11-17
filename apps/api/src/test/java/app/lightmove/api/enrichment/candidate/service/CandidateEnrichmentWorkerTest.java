package app.lightmove.api.enrichment.candidate.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.candidate.model.CandidateCapturedEvent;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.candidate.service.CandidateService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class CandidateEnrichmentWorkerTest {

    // The vanity profile can be filed by another capture between the lookup and the write: V95's unique index
    // refuses the address change, and that must leave the research already written standing.
    @Test
    @DisplayName("losing the race for the vanity profile costs the address change, never the research")
    void aLostVanityRaceKeepsTheResearch() {
        UUID projectId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        EnrichedProfile research = new EnrichedProfile("Group CFO", null, null, null, null, null, null,
                null, null, null, null, null, null);
        LinkedInProfileEnricher enricher = mock(LinkedInProfileEnricher.class);
        when(enricher.fetch(any())).thenReturn(Optional.of(research));
        CandidateService candidates = mock(CandidateService.class);
        doThrow(new DataIntegrityViolationException("app_lm_person_profile_slug_uk"))
                .when(candidates).adoptPublicProfile(projectId, candidateId, research);
        CandidateEnrichmentWorker worker = new CandidateEnrichmentWorker(enricher, candidates);

        assertThatCode(() -> worker.enrich(new CandidateCapturedEvent(candidateId, projectId,
                "https://www.linkedin.com/in/ACoAAB1234567890abcdefGHIJ"))).doesNotThrowAnyException();

        verify(candidates).applyResearch(projectId, candidateId, research);
        verify(candidates).adoptPublicProfile(projectId, candidateId, research);
    }
}
