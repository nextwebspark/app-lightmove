package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.ConsentAnsweredVia;
import app.lightmove.api.candidate.constant.ConsentEventAction;
import app.lightmove.api.candidate.constant.ConsentEventVia;
import app.lightmove.api.candidate.constant.ConsentPurpose;
import app.lightmove.api.candidate.constant.ConsentStatus;
import app.lightmove.api.candidate.model.ConsentPriorState;
import app.lightmove.api.candidate.model.PersonConsent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Reads and writes a person's consents and their append-only history (V223). */
@Component
@RequiredArgsConstructor
class PersonConsentStore {

    private static final String COLUMNS = """
            id, person_id, purpose, project_id, status, requested_at, requested_by, request_link_id,
            request_expires_at, answered_at, answered_via, valid_until, client_named, wording_version, wording,
            request_wording_version, request_wording, request_client_named, request_months""";

    private final NamedParameterJdbcTemplate jdbc;

    List<PersonConsent> consentsOf(UUID personId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM app_lm_person_consent WHERE person_id = :personId"
                + " ORDER BY purpose, created_at", new MapSqlParameterSource("personId", personId), this::consentOf);
    }

    Map<UUID, List<PersonConsent>> consentsOfEach(Collection<UUID> personIds) {
        Map<UUID, List<PersonConsent>> byPerson = new LinkedHashMap<>();
        personIds.forEach(id -> byPerson.put(id, new ArrayList<>()));
        if (personIds.isEmpty()) {
            return byPerson;
        }
        jdbc.query("SELECT " + COLUMNS + " FROM app_lm_person_consent WHERE person_id IN (:personIds)",
                new MapSqlParameterSource("personIds", personIds), this::consentOf)
                .forEach(consent -> byPerson.get(consent.personId()).add(consent));
        return byPerson;
    }

    List<PersonConsent> askedOnLink(UUID linkId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM app_lm_person_consent WHERE request_link_id = :linkId"
                + " ORDER BY purpose", new MapSqlParameterSource("linkId", linkId), this::consentOf);
    }

    /** The answers the history records on the link, by consent: what a reopened request page says was answered. */
    Map<UUID, ConsentStatus> answersOnLink(UUID linkId) {
        Map<UUID, ConsentStatus> byConsent = new LinkedHashMap<>();
        jdbc.query("""
                SELECT consent_id, status FROM app_lm_person_consent_event
                WHERE link_id = :linkId AND action = 'ANSWERED' ORDER BY occurred_at
                """, new MapSqlParameterSource("linkId", linkId), row -> {
                    byConsent.put(row.getObject("consent_id", UUID.class),
                            ConsentStatus.valueOf(row.getString("status")));
                });
        return byConsent;
    }

    /**
     * Opens a request on the purpose: a row nobody asked about yet is made, any other reopened as asked — except a
     * given consent, which stays given while its renewal waits. The request's words and terms wait beside the answer
     * already given, never over it, until an answer copies them across. Answers what to put back if the email is
     * refused.
     */
    ConsentPriorState openRequest(UUID workspaceId, UUID personId, ConsentPurpose purpose, UUID projectId,
                                  UUID requestedBy, UUID linkId, Instant now, Instant expiresAt, boolean clientNamed,
                                  String wordingVersion, String wording, Integer months) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("workspaceId", workspaceId)
                .addValue("personId", personId)
                .addValue("purpose", purpose.name())
                .addValue("projectId", projectId)
                .addValue("requestedBy", requestedBy)
                .addValue("linkId", linkId)
                .addValue("now", Timestamp.from(now))
                .addValue("expiresAt", Timestamp.from(expiresAt))
                .addValue("clientNamed", clientNamed)
                .addValue("wordingVersion", wordingVersion)
                .addValue("wording", wording)
                .addValue("months", months);
        Optional<PersonConsent> held = jdbc.query("SELECT " + COLUMNS + " FROM app_lm_person_consent"
                        + " WHERE person_id = :personId AND purpose = :purpose"
                        + " AND project_id IS NOT DISTINCT FROM :projectId FOR UPDATE", parameters, this::consentOf)
                .stream().findFirst();
        ConsentPriorState prior;
        UUID consentId;
        ConsentStatus status;
        if (held.isEmpty()) {
            consentId = UUID.randomUUID();
            status = ConsentStatus.REQUESTED;
            jdbc.update("""
                    INSERT INTO app_lm_person_consent (id, workspace_id, person_id, purpose, project_id, status,
                        requested_at, requested_by, request_link_id, request_expires_at, request_client_named,
                        request_wording_version, request_wording, request_months)
                    VALUES (:id, :workspaceId, :personId, :purpose, :projectId, 'REQUESTED', :now, :requestedBy,
                            :linkId, :expiresAt, :clientNamed, :wordingVersion, :wording, :months)
                    """, parameters.addValue("id", consentId));
            prior = new ConsentPriorState(consentId, true, null, null, null, null, null, null, null, false, null);
        } else {
            PersonConsent consent = held.get();
            consentId = consent.id();
            status = consent.status() == ConsentStatus.GIVEN ? ConsentStatus.GIVEN : ConsentStatus.REQUESTED;
            jdbc.update("""
                    UPDATE app_lm_person_consent SET status = :status, requested_at = :now, requested_by = :requestedBy,
                        request_link_id = :linkId, request_expires_at = :expiresAt,
                        request_client_named = :clientNamed, request_wording_version = :wordingVersion,
                        request_wording = :wording, request_months = :months
                    WHERE id = :id
                    """, parameters.addValue("id", consentId).addValue("status", status.name()));
            prior = new ConsentPriorState(consentId, false, consent.status(), consent.requestedAt(),
                    consent.requestedBy(), consent.requestLinkId(), consent.requestExpiresAt(),
                    consent.requestWordingVersion(), consent.requestWording(), consent.requestClientNamed(),
                    consent.requestMonths());
        }
        appendEvent(workspaceId, personId, consentId, ConsentEventAction.REQUESTED, status,
                requestedBy == null ? ConsentEventVia.SYSTEM : ConsentEventVia.STAFF, linkId, requestedBy,
                wordingVersion, wording, now);
        return prior;
    }

    /**
     * An answer on the page, while its request is still open: the words answered become the consent's own. False when
     * it was already answered or has closed.
     */
    boolean answer(UUID consentId, UUID linkId, ConsentStatus answer, Instant now, Instant validUntil) {
        return jdbc.update("""
                UPDATE app_lm_person_consent SET status = :status, answered_at = :now, answered_via = 'PAGE',
                    valid_until = :validUntil, request_expires_at = NULL, wording = request_wording,
                    wording_version = request_wording_version, client_named = request_client_named
                WHERE id = :id AND request_link_id = :linkId AND request_expires_at > :now
                """, new MapSqlParameterSource("id", consentId)
                .addValue("linkId", linkId)
                .addValue("status", answer.name())
                .addValue("now", Timestamp.from(now))
                .addValue("validUntil", validUntil == null ? null : Timestamp.from(validUntil))) > 0;
    }

    /** A no to a renewal: the consent stands until its own date, under its own words, and only the request closes. */
    boolean closeRenewal(UUID consentId, UUID linkId, Instant now) {
        return jdbc.update("""
                UPDATE app_lm_person_consent SET request_expires_at = NULL
                WHERE id = :id AND request_link_id = :linkId AND request_expires_at > :now AND status = 'GIVEN'
                """, new MapSqlParameterSource("id", consentId)
                .addValue("linkId", linkId)
                .addValue("now", Timestamp.from(now))) > 0;
    }

    boolean withdraw(UUID consentId, UUID personId, Instant now) {
        return jdbc.update("""
                UPDATE app_lm_person_consent SET status = 'WITHDRAWN', answered_at = :now, answered_via = 'PAGE',
                    valid_until = NULL, request_expires_at = NULL
                WHERE id = :id AND person_id = :personId AND status = 'GIVEN'
                """, new MapSqlParameterSource("id", consentId)
                .addValue("personId", personId)
                .addValue("now", Timestamp.from(now))) > 0;
    }

    /**
     * An answer given another way, recorded by a colleague; closes any request still open on the purpose. It clears
     * {@code requested_at} too: a day recorded by hand can fall before the request, and the renewal scan would then
     * never see the answer as newer than the asking.
     */
    UUID recordByHand(UUID workspaceId, UUID personId, ConsentPurpose purpose, UUID projectId, ConsentStatus answer,
                      Instant answeredAt, Instant validUntil) {
        return jdbc.queryForObject("""
                INSERT INTO app_lm_person_consent (workspace_id, person_id, purpose, project_id, status, answered_at,
                                                   answered_via, valid_until)
                VALUES (:workspaceId, :personId, :purpose, :projectId, :status, :answeredAt, 'BY_HAND', :validUntil)
                ON CONFLICT (person_id, purpose, project_id) DO UPDATE SET status = EXCLUDED.status,
                    answered_at = EXCLUDED.answered_at, answered_via = 'BY_HAND', valid_until = EXCLUDED.valid_until,
                    request_expires_at = NULL, requested_at = NULL
                RETURNING id
                """, new MapSqlParameterSource("workspaceId", workspaceId)
                .addValue("personId", personId)
                .addValue("purpose", purpose.name())
                .addValue("projectId", projectId)
                .addValue("status", answer.name())
                .addValue("answeredAt", Timestamp.from(answeredAt))
                .addValue("validUntil", validUntil == null ? null : Timestamp.from(validUntil)), UUID.class);
    }

    /**
     * A refused request undone: the row it made is deleted, any other put back as it stood — but only while the row is
     * still that request's. The send runs after the commit, so an answer recorded by hand or a newer request may have
     * landed since, and stands.
     */
    void restore(ConsentPriorState prior, UUID refusedLinkId, Instant refusedRequestedAt) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("id", prior.consentId())
                .addValue("refusedLinkId", refusedLinkId)
                .addValue("refusedRequestedAt", Timestamp.from(refusedRequestedAt));
        String stillThatRequest = " AND request_link_id = :refusedLinkId AND requested_at = :refusedRequestedAt";
        if (prior.created()) {
            jdbc.update("DELETE FROM app_lm_person_consent WHERE id = :id" + stillThatRequest, parameters);
            return;
        }
        jdbc.update("""
                UPDATE app_lm_person_consent SET status = :status, requested_at = :requestedAt,
                    requested_by = :requestedBy, request_link_id = :linkId, request_expires_at = :expiresAt,
                    request_wording_version = :wordingVersion, request_wording = :wording,
                    request_client_named = :clientNamed, request_months = :months
                WHERE id = :id""" + stillThatRequest, parameters.addValue("status", prior.status().name())
                .addValue("requestedAt", timestampOf(prior.requestedAt()))
                .addValue("requestedBy", prior.requestedBy())
                .addValue("linkId", prior.requestLinkId())
                .addValue("expiresAt", timestampOf(prior.requestExpiresAt()))
                .addValue("wordingVersion", prior.requestWordingVersion())
                .addValue("wording", prior.requestWording())
                .addValue("clientNamed", prior.requestClientNamed())
                .addValue("months", prior.requestMonths()));
    }

    /**
     * Requests past their date close: an unanswered one becomes expired, an unanswered renewal leaves the consent as
     * given. Answers every row it closed.
     */
    List<PersonConsent> closeLapsedRequests(UUID workspaceId, Instant now, int limit) {
        return jdbc.query("""
                UPDATE app_lm_person_consent
                   SET status = CASE WHEN status = 'REQUESTED' THEN 'EXPIRED' ELSE status END,
                       request_expires_at = NULL
                 WHERE id IN (SELECT id FROM app_lm_person_consent
                               WHERE workspace_id = :workspaceId AND request_expires_at <= :now
                               ORDER BY request_expires_at LIMIT :limit FOR UPDATE SKIP LOCKED)
                RETURNING""" + " " + COLUMNS, new MapSqlParameterSource("workspaceId", workspaceId)
                .addValue("now", Timestamp.from(now))
                .addValue("limit", limit), this::consentOf);
    }

    List<PersonConsent> expireLapsedConsents(UUID workspaceId, Instant now, int limit) {
        return jdbc.query("""
                UPDATE app_lm_person_consent SET status = 'EXPIRED'
                 WHERE id IN (SELECT id FROM app_lm_person_consent
                               WHERE workspace_id = :workspaceId AND status = 'GIVEN' AND valid_until <= :now
                               ORDER BY valid_until LIMIT :limit FOR UPDATE SKIP LOCKED)
                RETURNING""" + " " + COLUMNS, new MapSqlParameterSource("workspaceId", workspaceId)
                .addValue("now", Timestamp.from(now))
                .addValue("limit", limit), this::consentOf);
    }

    /**
     * Keep-on-file consents ending within the window whose holder has not been asked since giving them, a page at a
     * time after {@code after} (null for the first page), so the people no email may reach never hold back the rest.
     */
    List<PersonConsent> dueRenewal(UUID workspaceId, Instant now, Instant windowEnd, PersonConsent after, int limit) {
        return jdbc.query("SELECT " + COLUMNS + """
                 FROM app_lm_person_consent
                 WHERE workspace_id = :workspaceId AND purpose = 'KEEP_ON_FILE' AND status = 'GIVEN'
                   AND valid_until > :now AND valid_until <= :windowEnd AND request_expires_at IS NULL
                   AND (requested_at IS NULL OR requested_at < answered_at)
                   AND (CAST(:afterUntil AS timestamptz) IS NULL
                        OR (valid_until, id) > (CAST(:afterUntil AS timestamptz), CAST(:afterId AS uuid)))
                 ORDER BY valid_until, id LIMIT :limit
                """, new MapSqlParameterSource("workspaceId", workspaceId)
                .addValue("now", Timestamp.from(now))
                .addValue("windowEnd", Timestamp.from(windowEnd))
                .addValue("afterUntil", after == null ? null : Timestamp.from(after.validUntil()))
                .addValue("afterId", after == null ? null : after.id())
                .addValue("limit", limit), this::consentOf);
    }

    void appendEvent(UUID workspaceId, UUID personId, UUID consentId, ConsentEventAction action, ConsentStatus status,
                     ConsentEventVia via, UUID linkId, UUID actorUserId, String wordingVersion, String wording,
                     Instant at) {
        jdbc.update("""
                INSERT INTO app_lm_person_consent_event (workspace_id, person_id, consent_id, action, status, via,
                    link_id, actor_user_id, wording_version, wording, occurred_at)
                VALUES (:workspaceId, :personId, :consentId, :action, :status, :via, :linkId, :actor, :wordingVersion,
                        :wording, :at)
                """, new MapSqlParameterSource("workspaceId", workspaceId)
                .addValue("personId", personId)
                .addValue("consentId", consentId)
                .addValue("action", action.name())
                .addValue("status", status.name())
                .addValue("via", via.name())
                .addValue("linkId", linkId)
                .addValue("actor", actorUserId)
                .addValue("wordingVersion", wordingVersion)
                .addValue("wording", wording)
                .addValue("at", Timestamp.from(at)));
    }

    private PersonConsent consentOf(ResultSet row, int index) throws SQLException {
        String via = row.getString("answered_via");
        return new PersonConsent(row.getObject("id", UUID.class), row.getObject("person_id", UUID.class),
                ConsentPurpose.valueOf(row.getString("purpose")), row.getObject("project_id", UUID.class),
                ConsentStatus.valueOf(row.getString("status")), instantOf(row, "requested_at"),
                row.getObject("requested_by", UUID.class), row.getObject("request_link_id", UUID.class),
                instantOf(row, "request_expires_at"), instantOf(row, "answered_at"),
                via == null ? null : ConsentAnsweredVia.valueOf(via), instantOf(row, "valid_until"),
                row.getBoolean("client_named"), row.getString("wording_version"), row.getString("wording"),
                row.getString("request_wording_version"), row.getString("request_wording"),
                row.getBoolean("request_client_named"), row.getObject("request_months", Integer.class));
    }

    private static Instant instantOf(ResultSet row, String column) throws SQLException {
        Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestampOf(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
