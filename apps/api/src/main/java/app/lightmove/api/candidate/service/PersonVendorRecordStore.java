package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.PersonResearchOnFile;
import app.lightmove.api.candidate.model.ResearchedProfileDetails;
import app.lightmove.api.candidate.model.VendorProfileRecord;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code app_lm_person_vendor_record} (V229): each vendor's latest answer for a person, kept while the person is held,
 * and the typed reading laid together across vendors. ContactOut reads first, then the people dataset, the live
 * scrape and the profile index — the people cache's own precedence, freshest titles first.
 */
@Component
@RequiredArgsConstructor
public class PersonVendorRecordStore {

    private static final List<String> PRECEDENCE = List.of("contactout", "brightdata", "harvestapi",
            "brightdata-business");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public void keep(UUID workspaceId, UUID personId, Collection<VendorProfileRecord> records) {
        for (VendorProfileRecord record : records) {
            if (record.record() == null || record.record().isBlank() || !PRECEDENCE.contains(record.provider())) {
                continue;
            }
            jdbc.update("""
                    INSERT INTO app_lm_person_vendor_record
                        (person_id, workspace_id, provider, record, details, vendor_seen_at, fetched_at)
                    VALUES (:personId, :workspaceId, :provider, CAST(:record AS jsonb), CAST(:details AS jsonb),
                            :vendorSeenAt, now())
                    ON CONFLICT (person_id, provider) DO UPDATE SET
                        record = EXCLUDED.record, details = EXCLUDED.details,
                        vendor_seen_at = EXCLUDED.vendor_seen_at, fetched_at = EXCLUDED.fetched_at""",
                    new MapSqlParameterSource()
                            .addValue("personId", personId)
                            .addValue("workspaceId", workspaceId)
                            .addValue("provider", record.provider())
                            .addValue("record", record.record())
                            .addValue("details", json.writeValueAsString(record.details()))
                            .addValue("vendorSeenAt", record.vendorSeenAt() == null ? null
                                    : Timestamp.from(record.vendorSeenAt())));
        }
    }

    public PersonResearchOnFile researchOf(UUID workspaceId, UUID personId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT provider, details::text AS details, vendor_seen_at FROM app_lm_person_vendor_record
                WHERE workspace_id = :workspaceId AND person_id = :personId""",
                Map.of("workspaceId", workspaceId, "personId", personId));
        ResearchedProfileDetails merged = null;
        Instant seenAt = null;
        for (String provider : PRECEDENCE) {
            for (Map<String, Object> row : rows) {
                if (!provider.equals(row.get("provider"))) {
                    continue;
                }
                ResearchedProfileDetails details = json.readValue((String) row.get("details"),
                        ResearchedProfileDetails.class);
                merged = merged == null ? details : merged.orElse(details);
                if (row.get("vendor_seen_at") instanceof Timestamp seen
                        && (seenAt == null || seen.toInstant().isAfter(seenAt))) {
                    seenAt = seen.toInstant();
                }
            }
        }
        return new PersonResearchOnFile(merged, seenAt);
    }
}
