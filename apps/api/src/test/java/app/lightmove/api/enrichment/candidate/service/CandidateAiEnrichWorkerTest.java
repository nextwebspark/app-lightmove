package app.lightmove.api.enrichment.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.lightmove.api.candidate.constant.AiEnrichTrigger;
import app.lightmove.api.candidate.model.CandidateAiEnrichRequested;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.EnrichmentSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CandidateAiEnrichWorkerTest {

    private static final int CONCURRENT_RUNS = 4;

    @Test
    @DisplayName("a run past the four at once is queued and its thread let go, then run once a slot frees")
    void aRunPastTheLimitIsQueuedNotParked() throws Exception {
        CountDownLatch fourRunning = new CountDownLatch(CONCURRENT_RUNS);
        CountDownLatch release = new CountDownLatch(1);
        Set<UUID> ran = ConcurrentHashMap.newKeySet();
        CandidateService candidates = mock(CandidateService.class);
        when(candidates.dossierOf(any(UUID.class), any(UUID.class))).thenAnswer(call -> {
            ran.add(call.getArgument(1));
            fourRunning.countDown();
            release.await(5, TimeUnit.SECONDS);
            return Optional.empty();
        });
        CandidateAiEnrichWorker worker = workerOver(candidates);

        try (ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_RUNS + 1)) {
            List<UUID> first = new ArrayList<>();
            for (int run = 0; run < CONCURRENT_RUNS; run++) {
                UUID candidateId = UUID.randomUUID();
                first.add(candidateId);
                pool.execute(() -> worker.enrich(requestFor(candidateId)));
            }
            assertThat(fourRunning.await(5, TimeUnit.SECONDS)).isTrue();

            UUID fifth = UUID.randomUUID();
            CompletableFuture<Void> handedOver = CompletableFuture.runAsync(() -> worker.enrich(requestFor(fifth)), pool);
            handedOver.get(1, TimeUnit.SECONDS);
            assertThat(ran).containsExactlyInAnyOrderElementsOf(first);

            release.countDown();
        }

        assertThat(ran).hasSize(CONCURRENT_RUNS + 1);
    }

    private static CandidateAiEnrichWorker workerOver(CandidateService candidates) {
        EnrichmentSettings settings = mock(EnrichmentSettings.class);
        when(settings.aiEnrichOnCapture()).thenReturn(true);
        LightMoveProperties properties = mock(LightMoveProperties.class);
        when(properties.enrichment()).thenReturn(settings);
        return new CandidateAiEnrichWorker(mock(BackgroundProposer.class), candidates,
                mock(CandidateMaterialReader.class), mock(AiDraftWriter.class), mock(LlmBudgetGuard.class),
                mock(AuditService.class), properties);
    }

    private static CandidateAiEnrichRequested requestFor(UUID candidateId) {
        return new CandidateAiEnrichRequested(candidateId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                AiEnrichTrigger.SOURCING);
    }
}
