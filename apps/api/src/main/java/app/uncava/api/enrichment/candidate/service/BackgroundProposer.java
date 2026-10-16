package app.uncava.api.enrichment.candidate.service;

import app.uncava.api.candidate.model.CandidateDossier;
import app.uncava.api.candidate.model.InferredBackground;
import app.uncava.api.candidate.model.NationalityReading;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Asks the background call and the nationality classifier side by side — each is its own model call of up to the
 * request timeout, and a consultant is waiting on the button. Used by the button and the automatic run alike.
 */
@Component
@RequiredArgsConstructor
class BackgroundProposer {

    private final CandidateAiEnricher enricher;
    private final CandidateNationalityClassifier nationalityClassifier;

    BackgroundProposals propose(CandidateDossier dossier, MaterialText material, BackgroundNeeds needs) {
        try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<InferredBackground> background = CompletableFuture.supplyAsync(
                    () -> needs.inferring() ? enricher.infer(dossier, material).orElse(null) : null, threads);
            CompletableFuture<NationalityReading> reading = CompletableFuture.supplyAsync(
                    () -> needs.classifying() ? nationalityClassifier.classify(dossier, material).orElse(null) : null,
                    threads);
            return new BackgroundProposals(background.join(), reading.join());
        } catch (CompletionException failed) {
            if (failed.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw failed;
        }
    }
}
