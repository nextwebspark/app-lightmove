package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.constant.PrivacyLinkPurpose;
import app.lightmove.api.candidate.constant.PrivacyNoticeChannel;
import app.lightmove.api.candidate.model.CandidatePrivacySettings;
import app.lightmove.api.candidate.model.OutreachPrivacyFooter;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.repository.PersonRepository;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.DataProtectionSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.security.token.Tokens;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

/**
 * The privacy footer every outreach email ends with (#924), behind {@code DATA_PROTECTION_ENABLED}: the firm, where the
 * person's details came from, its notice, and a link to stop emails. Each email gets a stop link of its own, minted in
 * the transaction that prepares the send, which is also the {@code List-Unsubscribe} header's one-click target.
 */
@Service
@RequiredArgsConstructor
public class OutreachPrivacyService {

    /** An opt-out link answers long after the email: people unsubscribe from old mail. */
    static final Duration OPT_OUT_LINK_LIFETIME = Duration.ofDays(365);

    private static final String PERSON_TARGET = "person";

    private final LightMoveProperties properties;
    private final NamedParameterJdbcTemplate jdbc;
    private final PersonRepository people;
    private final CandidatePrivacySettingsService settings;
    private final PersonActivityRecorder activity;
    private final AuditService audit;

    public boolean isOffered() {
        DataProtectionSettings dataProtection = properties.dataProtection();
        return dataProtection != null && dataProtection.enabled();
    }

    /** Null when data protection is off: the email goes as it did before #924. */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutreachPrivacyFooter footerFor(UUID workspaceId, UUID personId, UUID senderUserId, Instant now) {
        if (!isOffered() || personId == null) {
            return null;
        }
        Person person = people.requireInWorkspace(personId, workspaceId);
        CandidatePrivacySettings firm = settings.settingsOf(workspaceId);
        String token = Tokens.generate();
        jdbc.update("""
                INSERT INTO app_lm_person_privacy_link (workspace_id, person_id, token_hash, purpose, sent_by,
                                                        created_at, expires_at)
                VALUES (:workspaceId, :personId, :tokenHash, :purpose, :sentBy, :now, :expiresAt)
                """, new MapSqlParameterSource("workspaceId", workspaceId)
                .addValue("personId", personId)
                .addValue("tokenHash", Tokens.hash(token))
                .addValue("purpose", PrivacyLinkPurpose.OPT_OUT.name())
                .addValue("sentBy", senderUserId)
                .addValue("now", Timestamp.from(now))
                .addValue("expiresAt", Timestamp.from(now.plus(OPT_OUT_LINK_LIFETIME))));
        String baseUrl = properties.web().baseUrl();
        String page = baseUrl + "/privacy/" + token;
        String notice = firm.noticeUrl() == null ? page : firm.noticeUrl();
        String html = "<p style=\"margin:16px 0 0;padding-top:10px;border-top:1px solid #e5e7eb;font-size:12px;"
                + "line-height:1.55;color:#6b7280\">" + escaped(statementOf(person, firm))
                + " <a href=\"" + escaped(notice) + "\" style=\"color:#2563eb\">Our privacy notice</a> &middot; "
                + "<a href=\"" + escaped(page + "?action=opt-out") + "\" style=\"color:#2563eb\">Stop emails from us</a>"
                + "</p>";
        return new OutreachPrivacyFooter(html, baseUrl + "/api/v1/privacy/links/" + token + "/opt-out",
                firm.isReadyForNotices());
    }

    /**
     * The footer's sentence for each person, as the composer and Add to sequence preview it; empty when data
     * protection is off.
     */
    @Transactional(readOnly = true)
    public Map<UUID, String> footerStatementsOf(UUID workspaceId, Collection<UUID> personIds) {
        if (!isOffered() || personIds.isEmpty()) {
            return Map.of();
        }
        CandidatePrivacySettings firm = settings.settingsOf(workspaceId);
        return people.findByWorkspaceIdAndIdIn(workspaceId, personIds).stream()
                .collect(Collectors.toMap(Person::getId, person -> statementOf(person, firm)));
    }

    /**
     * An outreach email whose footer carried the firm's privacy details told a person never told before: it is
     * recorded as their notice. Someone already told keeps the telling they had.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordToldByOutreach(UUID workspaceId, UUID personId, UUID senderUserId, Instant sentAt) {
        Person person = people.findByIdAndWorkspaceId(personId, workspaceId).orElse(null);
        if (person == null || person.getPrivacyNotifiedAt() != null) {
            return;
        }
        person.recordNotified(sentAt, PrivacyNoticeChannel.OUTREACH_EMAIL);
        activity.record(person, null, senderUserId, PersonActivityKind.PRIVACY_NOTICE_SENT,
                PersonActivityDetails.of("channel", PrivacyNoticeChannel.OUTREACH_EMAIL));
        audit.event(ProjectEventType.PERSON_PRIVACY_NOTICE_SENT)
                .actor(senderUserId).workspace(workspaceId).target(PERSON_TARGET, personId)
                .detail("channel", PrivacyNoticeChannel.OUTREACH_EMAIL.value())
                .record();
    }

    /**
     * When the person last stopped emails, from any of their links: a notice's page offers Stop emails too, so the
     * purpose is not filtered. Null once do not contact has been cleared.
     */
    @Transactional(readOnly = true)
    public Instant optedOutAtOf(Person person) {
        if (!person.isDoNotContact()) {
            return null;
        }
        Timestamp latest = jdbc.queryForObject("""
                SELECT max(used_at) FROM app_lm_person_privacy_link
                WHERE workspace_id = :workspaceId AND person_id = :personId
                """, new MapSqlParameterSource("workspaceId", person.getWorkspaceId())
                .addValue("personId", person.getId()), Timestamp.class);
        return latest == null ? null : latest.toInstant();
    }

    private static String statementOf(Person person, CandidatePrivacySettings firm) {
        String holder = firm.controllerName() == null ? "We hold" : firm.controllerName() + " holds";
        return holder + " your professional details, which came from "
                + PrivacyNoticeWording.sourceOf(person.getSource()) + ".";
    }

    private static String escaped(String text) {
        return HtmlUtils.htmlEscape(text, "UTF-8");
    }
}
