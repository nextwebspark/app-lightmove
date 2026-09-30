package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.enrichment.candidate.model.BrightDataPeopleHits;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * The vendor people cache (V87): every record a billed search or lookup returned, and which records
 * each search returned. {@code CachedCompanyStore}'s shape — its own short transactions so the vendor
 * call between a read and a write holds no connection, and upserts so two runs racing on one person
 * land on one row.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CachedPeopleStore {

    private static final String UPSERT_PERSON = """
            INSERT INTO app_lm_vendor_person (linkedin_slug, provider, fetched_at, current_company_slug,
                                              country_code, position, raw)
            VALUES (?, ?, now(), ?, ?, ?, CAST(? AS jsonb))
            ON CONFLICT (linkedin_slug) DO UPDATE SET
                provider             = EXCLUDED.provider,
                fetched_at           = EXCLUDED.fetched_at,
                current_company_slug = EXCLUDED.current_company_slug,
                country_code         = EXCLUDED.country_code,
                position             = EXCLUDED.position,
                raw                  = EXCLUDED.raw
            """;

    private static final String UPSERT_SEARCH = """
            INSERT INTO app_lm_vendor_people_search (query_key, company_slug, slugs, total_hits, fetched_at)
            VALUES (?, ?, ?, ?, now())
            ON CONFLICT (query_key) DO UPDATE SET
                company_slug = EXCLUDED.company_slug,
                slugs        = EXCLUDED.slugs,
                total_hits   = EXCLUDED.total_hits,
                fetched_at   = EXCLUDED.fetched_at
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<BrightDataPerson> find(String linkedinSlug, Instant freshAfter) {
        return jdbc.query("SELECT raw FROM app_lm_vendor_person WHERE linkedin_slug = ? AND fetched_at > ?",
                (rs, row) -> rs.getString("raw"), key(linkedinSlug), Timestamp.from(freshAfter))
                .stream().findFirst().flatMap(this::personOf);
    }

    /** Everyone fresh on file at one employer that one provider returned, newest first. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<BrightDataPerson> atCompany(String companySlug, String provider, Instant freshAfter, int limit) {
        return jdbc.query("""
                        SELECT raw FROM app_lm_vendor_person
                        WHERE current_company_slug = ? AND provider = ? AND fetched_at > ?
                        ORDER BY fetched_at DESC LIMIT ?
                        """,
                (rs, row) -> rs.getString("raw"), key(companySlug), provider, Timestamp.from(freshAfter), limit)
                .stream().flatMap(raw -> personOf(raw).stream()).toList();
    }

    /**
     * The people one earlier search returned, in the order it returned them, and how many it matched in
     * all — empty when the search is not on file or has aged out, and also when any of its people has,
     * since a partial answer to a cached question would silently be a smaller one.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<BrightDataPeopleHits> answerTo(String queryKey, Instant freshAfter) {
        List<StoredSearch> found = jdbc.query(
                "SELECT slugs, total_hits FROM app_lm_vendor_people_search WHERE query_key = ? AND fetched_at > ?",
                (rs, row) -> new StoredSearch(List.of((String[]) rs.getArray("slugs").getArray()),
                        rs.getObject("total_hits", Long.class)),
                queryKey, Timestamp.from(freshAfter));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        List<String> slugs = found.getFirst().slugs();
        Long totalHits = found.getFirst().totalHits();
        if (slugs.isEmpty()) {
            return Optional.of(BrightDataPeopleHits.of(List.of(), totalHits));
        }
        Map<String, BrightDataPerson> bySlug = new LinkedHashMap<>();
        jdbc.query(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "SELECT linkedin_slug, raw FROM app_lm_vendor_person WHERE linkedin_slug = ANY (?) AND fetched_at > ?");
            ps.setArray(1, con.createArrayOf("text", slugs.toArray(String[]::new)));
            ps.setTimestamp(2, Timestamp.from(freshAfter));
            return ps;
        }, rs -> {
            String slug = rs.getString("linkedin_slug");
            personOf(rs.getString("raw")).ifPresent(person -> bySlug.put(slug, person));
        });
        if (bySlug.size() < slugs.size()) {
            return Optional.empty();
        }
        List<BrightDataPerson> ordered = new ArrayList<>(slugs.size());
        slugs.forEach(slug -> ordered.add(bySlug.get(slug)));
        return Optional.of(BrightDataPeopleHits.of(ordered, totalHits));
    }

    /**
     * Where the people on file live, as LinkedIn spells it, most common first: the place alone, never
     * who lives there. What a location box can offer that a vendor search is sure to recognise.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<String> placesStartingWith(String prefix, int limit) {
        String pattern = prefix.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
                .replace("_", "\\_") + "%";
        return jdbc.queryForList("""
                        SELECT raw ->> 'city' AS place FROM app_lm_vendor_person
                        WHERE raw ->> 'city' IS NOT NULL AND lower(raw ->> 'city') LIKE ?
                        GROUP BY raw ->> 'city'
                        ORDER BY count(*) DESC, raw ->> 'city'
                        LIMIT ?
                        """, String.class, pattern, limit);
    }

    /** Every record a vendor call returned — each was billed, and each is kept. Records with no slug are not. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rememberAll(String provider, BrightDataPeopleHits answer) {
        List<Integer> keyed = IntStream.range(0, answer.hits().size())
                .filter(index -> hasSlug(answer.hits().get(index)))
                .boxed()
                .toList();
        if (keyed.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(UPSERT_PERSON, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int index) throws SQLException {
                BrightDataPerson person = answer.hits().get(keyed.get(index));
                String raw = answer.rawOf(keyed.get(index));
                ps.setString(1, key(person.linkedinId()));
                ps.setString(2, provider);
                ps.setString(3, person.currentCompany() == null ? null : key(person.currentCompany().companyId()));
                ps.setString(4, person.countryCode());
                ps.setString(5, person.position());
                ps.setString(6, raw != null ? raw : json.writeValueAsString(person));
            }

            @Override
            public int getBatchSize() {
                return keyed.size();
            }
        });
    }

    /** Deletes what has aged past the TTL, so a record is held no longer than it may be read. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void purgeFetchedBefore(Instant cutoff) {
        Timestamp before = Timestamp.from(cutoff);
        jdbc.update("DELETE FROM app_lm_vendor_people_search WHERE fetched_at < ?", before);
        jdbc.update("DELETE FROM app_lm_vendor_person WHERE fetched_at < ?", before);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rememberSearch(String queryKey, String companySlug, List<String> slugs, Long totalHits) {
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(UPSERT_SEARCH);
            ps.setString(1, queryKey);
            ps.setString(2, key(companySlug));
            ps.setArray(3, con.createArrayOf("text", slugs.stream().map(CachedPeopleStore::key).toArray(String[]::new)));
            ps.setObject(4, totalHits);
            return ps;
        });
    }

    /** A record written by an older shape that no longer binds is a miss, never a failed run. */
    private Optional<BrightDataPerson> personOf(String raw) {
        try {
            return Optional.of(json.readValue(raw, BrightDataPerson.class));
        } catch (RuntimeException unreadable) {
            log.warn("Unreadable cached person record: {}", unreadable.toString());
            return Optional.empty();
        }
    }

    /** The dataset keys on the lower-case slug; so does every row here. */
    private static String key(String slug) {
        return slug == null ? null : slug.toLowerCase(Locale.ROOT);
    }

    private static boolean hasSlug(BrightDataPerson person) {
        return person.linkedinId() != null && !person.linkedinId().isBlank();
    }

    private record StoredSearch(List<String> slugs, Long totalHits) {}
}
