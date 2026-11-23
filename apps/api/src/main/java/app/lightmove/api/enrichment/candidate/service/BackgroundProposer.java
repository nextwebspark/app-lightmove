package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.model.AiBackgroundSource;
import app.lightmove.api.candidate.model.InferredBackground;
import app.lightmove.api.candidate.model.NationalityReading;
import app.lightmove.api.core.llm.model.AttributedAnswer;
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

    private static final AttributedAnswer<InferredBackground> NOTHING_INFERRED = new AttributedAnswer<>(null, null);
    private static final AttributedAnswer<NationalityReading> NOTHING_READ = new AttributedAnswer<>(null, null);

    private final CandidateAiEnricher enricher;
    private final CandidateNationalityClassifier nationalityClassifier;

    BackgroundProposals propose(CandidateDossier dossier, MaterialText material, BackgroundNeeds needs) {
        try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<AttributedAnswer<InferredBackground>> background = CompletableFuture.supplyAsync(
                    () -> needs.inferring().isEmpty() ? NOTHING_INFERRED
                            : enricher.inferAttributed(dossier, material, needs.inferring()).orElse(NOTHING_INFERRED),
                    threads);
            CompletableFuture<AttributedAnswer<NationalityReading>> reading = CompletableFuture.supplyAsync(
                    () -> needs.classifying()
                            ? nationalityClassifier.classifyAttributed(dossier, material).orElse(NOTHING_READ)
                            : NOTHING_READ,
                    threads);
            return new BackgroundProposals(background.join().answer(), reading.join().answer(),
                    new AiBackgroundSource(background.join().answeredBy(), reading.join().answeredBy()));
        } catch (CompletionException failed) {
            if (failed.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw failed;
        }
    }
}
