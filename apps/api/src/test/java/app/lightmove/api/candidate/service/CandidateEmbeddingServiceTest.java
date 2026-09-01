package app.lightmove.api.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.StubEmbeddingModel;
import org.junit.jupiter.api.Test;

/** No GCP credentials or network calls — {@link StubEmbeddingModel} stands in for Vertex AI. */
class CandidateEmbeddingServiceTest {

    @Test
    void returnsTheVectorForTheGivenText() {
        CandidateEmbeddingService service = new CandidateEmbeddingService(new StubEmbeddingModel());

        assertThat(service.embed("10 years as CFO at a fintech scale-up.")).isEqualTo(new float[] {0.1f, 0.2f, 0.3f});
    }
}
