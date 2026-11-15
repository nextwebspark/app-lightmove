package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.PrivacyLinkPurpose;
import app.lightmove.api.candidate.constant.PrivacyNoticeSkipReason;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.core.config.LightMoveProperties;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * What privacy notices and consent requests (#923, #1267) share: one daily budget on Uncava's sender, and the people
 * no privacy email may reach. Both kinds of email count against the same day, under the same lock.
 */
@Component
class PrivacyEmailBudget {

    private static final List<String> BUDGETED = List.of(PrivacyLinkPurpose.NOTICE.name(),
            PrivacyLinkPurpose.CONSENT.name());

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final int perDay;

    PrivacyEmailBudget(NamedParameterJdbcTemplate jdbc, Clock clock, LightMoveProperties properties) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.perDay = properties.dataProtection().privacyNoticesPerDay();
    }

    /**
     * Held until the commit, so two sends at once cannot each read the same count and together pass the cap, nor both
     * find the same person not yet told.
     */
    void hold(UUID workspaceId) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))",
                Map.of("key", "privacy-notice:" + workspaceId));
    }

    /** How many more this workspace may send today; the cap is a brake on Uncava's sender, not a calendar. */
    int remainingToday(UUID workspaceId) {
        Instant midnight = LocalDate.now(clock).atStartOfDay().toInstant(ZoneOffset.UTC);
        Integer sent = jdbc.queryForObject("""
                SELECT count(*) FROM app_lm_person_privacy_link
                WHERE workspace_id = :workspaceId AND purpose IN (:purposes) AND created_at >= :since
                """, new MapSqlParameterSource("workspaceId", workspaceId)
                .addValue("purposes", BUDGETED)
                .addValue("since", Timestamp.from(midnight)), Integer.class);
        return perDay - (sent == null ? 0 : sent);
    }

    /** Someone no privacy email may reach, and why; null when one may go. */
    static PrivacyNoticeSkipReason unreachableReasonOf(Person person, Set<UUID> suppressed) {
        if (person.emailContacts().isEmpty()) {
            return PrivacyNoticeSkipReason.NO_EMAIL;
        }
        if (person.isDoNotContact()) {
            return PrivacyNoticeSkipReason.DO_NOT_CONTACT;
        }
        if (person.isProcessingRestricted()) {
            return PrivacyNoticeSkipReason.PROCESSING_RESTRICTED;
        }
        if (suppressed.contains(person.getId())) {
            return PrivacyNoticeSkipReason.SUPPRESSED;
        }
        return null;
    }
}
