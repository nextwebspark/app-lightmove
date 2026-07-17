package app.lightmove.api.company.service;

import app.lightmove.api.company.dto.CompanyDtos.TaxonomyMatch;
import app.lightmove.api.company.repository.CompanyTaxonomyRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

/**
 * Embeds a raw search query and finds the nearest taxonomy terms to it.
 *
 * <p>Uses the same embedding model/dimensionality as {@link TaxonomyBackfillRunner}
 * ({@code spring.ai.vertex.ai.embedding.text.model}, application.yml) — a query embedded with a
 * different model is cosine-meaningless against the taxonomy vectors.
 */
@Service
@RequiredArgsConstructor
public class TaxonomySearchService {

    private final EmbeddingModel embeddingModel;
    private final CompanyTaxonomyRepository taxonomyRepository;

    /** Top {@code limit} taxonomy terms nearest to {@code queryText}, ordered by descending score. */
    public List<TaxonomyMatch> findCandidates(String queryText, int limit) {
        float[] queryEmbedding = embeddingModel.embed(queryText);
        return taxonomyRepository.findNearest(queryEmbedding, limit);
    }
}
