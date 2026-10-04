package app.lightmove.api.core.llm.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/** The {@link EmbeddingModel} of a deployment with AI switched off; see {@link DisabledChatModel}. */
public class DisabledEmbeddingModel implements EmbeddingModel {

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        throw new IllegalStateException("AI is switched off here (lightmove.llm.enabled=false)");
    }

    @Override
    public float[] embed(Document document) {
        throw new IllegalStateException("AI is switched off here (lightmove.llm.enabled=false)");
    }
}
