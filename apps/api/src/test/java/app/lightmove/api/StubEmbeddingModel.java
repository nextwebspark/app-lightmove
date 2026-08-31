package app.lightmove.api;

import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * An {@link EmbeddingModel} that returns a fixed vector, for the same reason as
 * {@link StubChatModel}: the real Google GenAI auto-configuration is off under the {@code test}
 * profile, but {@link app.lightmove.api.candidate.service.CandidateEmbeddingService} still needs
 * an {@code EmbeddingModel} bean to wire the application context.
 */
public class StubEmbeddingModel implements EmbeddingModel {

    private static final float[] VECTOR = {0.1f, 0.2f, 0.3f};

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        return new EmbeddingResponse(List.of(new Embedding(VECTOR, 0)));
    }

    @Override
    public float[] embed(Document document) {
        return VECTOR;
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        public StubEmbeddingModel stubEmbeddingModel() {
            return new StubEmbeddingModel();
        }
    }
}
