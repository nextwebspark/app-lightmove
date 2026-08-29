package app.lightmove.api.core.llm.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper over the auto-configured Google GenAI {@link EmbeddingModel} — a seam for future
 * features (semantic company or candidate search) to depend on rather than the model bean itself.
 */
@Service
@RequiredArgsConstructor
public class CandidateEmbeddingService {

    private final EmbeddingModel embeddingModel;

    public float[] embed(String text) {
        return embeddingModel.embed(text);
    }
}
