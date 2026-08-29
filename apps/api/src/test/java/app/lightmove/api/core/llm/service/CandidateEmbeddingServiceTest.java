package app.lightmove.api.core.llm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/** No GCP credentials or network calls — a hand-rolled {@link EmbeddingModel} stands in for Vertex AI. */
class CandidateEmbeddingServiceTest {

    @Test
    void returnsTheVectorForTheGivenText() {
        float[] vector = {0.1f, 0.2f, 0.3f};
        CandidateEmbeddingService service = new CandidateEmbeddingService(new FixedVectorEmbeddingModel(vector));

        assertThat(service.embed("10 years as CFO at a fintech scale-up.")).isEqualTo(vector);
    }

    private static final class FixedVectorEmbeddingModel implements EmbeddingModel {
        private final float[] vector;

        private FixedVectorEmbeddingModel(float[] vector) {
            this.vector = vector;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            return new EmbeddingResponse(List.of(new Embedding(vector, 0)));
        }

        @Override
        public float[] embed(Document document) {
            return vector;
        }
    }
}
