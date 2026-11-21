package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.PersonResearchOnFile;
import app.lightmove.api.candidate.model.ResearchedProfileDetails;
import app.lightmove.api.candidate.model.VendorProfileRecord;
import app.lightmove.api.candidate.repository.PersonRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code app_lm_person_vendor_record} (V229): each vendor's latest answer for a person, kept while the person is held,
 * and the typed readings laid together in {@link VendorProfileRecord#PROVIDERS} order.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PersonVendorRecordStore {

    /** Far above any profile a vendor sends; a record past it is not kept rather than let it bloat the person. */
    static final int LONGEST_RECORD = 1_000_000;

    private final NamedParameterJdbcTemplate jdbc;
    private final PersonRepository people;
    private final ObjectMapper json;

    /**
     * Runs inside the filing's transaction, so nothing here may fail the filing: Postgres {@code jsonb} refuses a NUL
     * character, and the person row is flushed first so a person founded by this filing exists for the key to name.
     */
    public void keepFor(Person person, EnrichedProfile enriched) {
        List<VendorProfileRecord> kept = enriched.vendorRecords().stream()
                .filter(record -> VendorProfileRecord.PROVIDERS.contains(record.provider()))
                .filter(record -> record.record() != null && !record.record().isBlank())
                .toList();
        if (kept.isEmpty()) {
            return;
        }
        people.flush();
        for (VendorProfileRecord record : kept) {
            String stored = withoutNul(record.record());
            if (stored == null || stored.length() > LONGEST_RECORD) {
                log.warn("A {} record for person {} was not kept: unreadable or too long", record.provider(),
                        person.getId());
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
                            .addValue("personId", person.getId())
                            .addValue("workspaceId", person.getWorkspaceId())
                            .addValue("provider", record.provider())
                            .addValue("record", stored)
                            .addValue("details", withoutNul(json.writeValueAsString(record.details())))
                            .addValue("vendorSeenAt", record.vendorSeenAt() == null ? null
                                    : Timestamp.from(record.vendorSeenAt())));
        }
    }

    /** {@code researchSeenAt} is the newest any vendor read the profile, whichever vendor's field is shown. */
    public PersonResearchOnFile researchOf(UUID workspaceId, UUID personId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT provider, details::text AS details, vendor_seen_at FROM app_lm_person_vendor_record
                WHERE workspace_id = :workspaceId AND person_id = :personId""",
                Map.of("workspaceId", workspaceId, "personId", personId));
        ResearchedProfileDetails merged = null;
        Instant seenAt = null;
        for (Map<String, Object> row : rows.stream()
                .sorted(Comparator.comparingInt(row -> VendorProfileRecord.PROVIDERS.indexOf(row.get("provider"))))
                .toList()) {
            ResearchedProfileDetails details = json.readValue((String) row.get("details"),
                    ResearchedProfileDetails.class);
            merged = merged == null ? details : merged.orElse(details);
            if (row.get("vendor_seen_at") instanceof Timestamp seen
                    && (seenAt == null || seen.toInstant().isAfter(seenAt))) {
                seenAt = seen.toInstant();
            }
        }
        return new PersonResearchOnFile(merged, seenAt);
    }

    private String withoutNul(String record) {
        JsonNode tree;
        try {
            tree = json.readTree(record);
        } catch (RuntimeException unreadable) {
            return null;
        }
        return tree.isObject() ? json.writeValueAsString(stripNul(tree)) : null;
    }

    private JsonNode stripNul(JsonNode node) {
        if (node.isString()) {
            String text = node.asString();
            return text.indexOf('\u0000') < 0 ? node : json.getNodeFactory().stringNode(text.replace("\u0000", ""));
        }
        if (node instanceof ObjectNode object) {
            object.propertyNames().forEach(name -> object.set(name, stripNul(object.get(name))));
        } else if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                array.set(i, stripNul(array.get(i)));
            }
        }
        return node;
    }
}
