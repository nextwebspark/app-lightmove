package app.lightmove.api.company.service;

import app.lightmove.api.company.model.CompanyTaxonomyRow;
import app.lightmove.api.company.repository.CompanyRepository;
import app.lightmove.api.company.repository.CompanyTaxonomyRepository;
import app.lightmove.api.company.repository.CompanyTaxonomyRepository.CompanyCountUpdate;
import app.lightmove.api.company.repository.CompanyTaxonomyRepository.TaxonomyKey;
import app.lightmove.api.company.repository.CompanyTaxonomyRepository.UpsertRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Populates {@code app_lm_company_taxonomy} from the distinct {@code primary_industry}/
 * {@code industry_tags} values currently in {@code app_lm_companies}.
 *
 * <p>Never runs on a normal boot — gated behind the {@code taxonomy-backfill} profile, the same
 * deliberate-manual-action spirit as {@code ops/cloudsql/sync-companies.sh}:
 *
 * <pre>./mvnw spring-boot:run -Dspring-boot.run.profiles=local,taxonomy-backfill</pre>
 *
 * <p>Idempotent and safe to re-run after every company sync: a value already in the taxonomy table only
 * gets its {@code company_count} refreshed (cheap), not re-embedded (a real Vertex AI call, and one this
 * runner is deliberately stingy with). Only genuinely new values get embedded.
 */
@Component
@Profile("taxonomy-backfill")
@RequiredArgsConstructor
@Slf4j
public class TaxonomyBackfillRunner implements ApplicationRunner {

    /** Vertex AI's embedding endpoint accepts a batch per call; chunked to stay under any request-size limit. */
    private static final int EMBED_BATCH_SIZE = 100;

    private final CompanyRepository companies;
    private final CompanyTaxonomyRepository taxonomy;
    private final EmbeddingModel embeddingModel;

    @Override
    public void run(ApplicationArguments args) {
        List<CompanyTaxonomyRow> current = Stream.concat(
                        companies.distinctPrimaryIndustries().stream(),
                        companies.distinctIndustryTags().stream())
                .toList();

        Set<TaxonomyKey> existingKeys = taxonomy.findExistingKeys();

        List<CompanyTaxonomyRow> toEmbed = new ArrayList<>();
        List<CompanyCountUpdate> toRefresh = new ArrayList<>();
        for (CompanyTaxonomyRow row : current) {
            if (existingKeys.contains(new TaxonomyKey(row.type(), row.name()))) {
                toRefresh.add(new CompanyCountUpdate(row.type(), row.name(), row.companyCount()));
            } else {
                toEmbed.add(row);
            }
        }

        int embedded = 0;
        for (List<CompanyTaxonomyRow> chunk : partition(toEmbed, EMBED_BATCH_SIZE)) {
            List<float[]> embeddings = embeddingModel.embed(chunk.stream().map(CompanyTaxonomyRow::name).toList());
            List<UpsertRow> upserts = new ArrayList<>(chunk.size());
            for (int i = 0; i < chunk.size(); i++) {
                CompanyTaxonomyRow row = chunk.get(i);
                upserts.add(new UpsertRow(row.type(), row.name(), embeddings.get(i), row.companyCount()));
            }
            taxonomy.upsertAll(upserts);
            embedded += upserts.size();
        }

        if (!toRefresh.isEmpty()) {
            taxonomy.refreshCompanyCounts(toRefresh);
        }

        log.info("Taxonomy backfill complete: {} embedded, {} refreshed, {} total taxonomy terms",
                embedded, toRefresh.size(), current.size());
    }

    private static <T> List<List<T>> partition(List<T> items, int size) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < items.size(); i += size) {
            chunks.add(items.subList(i, Math.min(i + size, items.size())));
        }
        return chunks;
    }
}
