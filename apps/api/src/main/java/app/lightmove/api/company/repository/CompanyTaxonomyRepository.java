package app.lightmove.api.company.repository;

import app.lightmove.api.company.constant.TaxonomyType;
import app.lightmove.api.company.dto.CompanyDtos.TaxonomyMatch;
import com.pgvector.PGvector;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Hand-written JDBC over {@code app_lm_company_taxonomy}.
 *
 * <p>Not a Spring Data interface: the {@code vector} column has no first-class JPA mapping without a
 * custom Hibernate {@code UserType}, and this table only ever needs an idempotent upsert (the backfill)
 * and a nearest-neighbor read (query time) — both simpler as raw SQL than as a mapped entity.
 *
 * <p>Embeddings bind via {@link PGvector}, which wraps a {@code float[]} as a JDBC {@code PGobject} the
 * Postgres driver serializes natively — no special {@code sqlType} hint needed, unlike the array-overlap
 * workaround in {@link CompanyRepository}.
 */
@Repository
@RequiredArgsConstructor
public class CompanyTaxonomyRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** Every {@code (type, name)} pair already present — what the backfill diffs new values against. */
    public Set<TaxonomyKey> findExistingKeys() {
        String sql = "SELECT type, name FROM app_lm_company_taxonomy";
        List<TaxonomyKey> rows = jdbc.getJdbcOperations().query(sql, (rs, rowNum) ->
                new TaxonomyKey(TaxonomyType.valueOf(rs.getString("type")), rs.getString("name")));
        return new HashSet<>(rows);
    }

    /**
     * Insert new taxonomy rows, or refresh {@code embedding}/{@code company_count} on ones that already
     * exist. Idempotent by design — {@code TaxonomyBackfillRunner} re-runs this on every sync.
     */
    public void upsertAll(List<UpsertRow> rows) {
        String sql = """
                INSERT INTO app_lm_company_taxonomy (type, name, embedding, company_count, updated_at)
                VALUES (:type, :name, :embedding, :companyCount, now())
                ON CONFLICT (type, name) DO UPDATE
                SET embedding = EXCLUDED.embedding,
                    company_count = EXCLUDED.company_count,
                    updated_at = now()
                """;
        MapSqlParameterSource[] batch = rows.stream()
                .map(row -> new MapSqlParameterSource()
                        .addValue("type", row.type().name())
                        .addValue("name", row.name())
                        .addValue("embedding", toPGvector(row.embedding()))
                        .addValue("companyCount", row.companyCount()))
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate(sql, batch);
    }

    /**
     * Refresh only {@code company_count} — used for values that already have an embedding and don't
     * need re-embedding, just an up-to-date count.
     */
    public void refreshCompanyCounts(List<CompanyCountUpdate> updates) {
        String sql = """
                UPDATE app_lm_company_taxonomy
                SET company_count = :companyCount, updated_at = now()
                WHERE type = :type AND name = :name
                """;
        MapSqlParameterSource[] batch = updates.stream()
                .map(u -> new MapSqlParameterSource()
                        .addValue("type", u.type().name())
                        .addValue("name", u.name())
                        .addValue("companyCount", u.companyCount()))
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate(sql, batch);
    }

    /**
     * Nearest {@code limit} taxonomy terms to {@code queryEmbedding} by cosine distance. Score is
     * {@code 1 - distance}, so 1.0 is a perfect match and values closer to 0 are weaker — more intuitive
     * for an API response than raw distance. Relies on {@code app_lm_company_taxonomy_embedding_idx}
     * (HNSW, {@code vector_cosine_ops}), which Postgres's planner picks up automatically for
     * {@code ORDER BY embedding <=> ? LIMIT ?}.
     */
    public List<TaxonomyMatch> findNearest(float[] queryEmbedding, int limit) {
        String sql = """
                SELECT type, name, 1 - (embedding <=> :query) AS score
                FROM app_lm_company_taxonomy
                ORDER BY embedding <=> :query
                LIMIT :limit
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("query", toPGvector(queryEmbedding))
                .addValue("limit", limit);
        return jdbc.query(sql, params, (rs, rowNum) -> new TaxonomyMatch(
                TaxonomyType.valueOf(rs.getString("type")), rs.getString("name"), rs.getDouble("score")));
    }

    private static PGvector toPGvector(float[] embedding) {
        return new PGvector(embedding);
    }

    public record TaxonomyKey(TaxonomyType type, String name) {}

    public record UpsertRow(TaxonomyType type, String name, float[] embedding, int companyCount) {}

    public record CompanyCountUpdate(TaxonomyType type, String name, int companyCount) {}
}
