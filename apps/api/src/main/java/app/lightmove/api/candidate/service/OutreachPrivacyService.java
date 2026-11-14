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
import app.lightmove.api.core.email.service.EmailTemplates;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The privacy footer every outreach email ends with, behind {@code DATA_PROTECTION_ENABLED}: the firm, where the
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
    private final PrivacyLinkStore privacyLinks;
    private final EmailTemplates templates;
    private final PersonRepository people;
    private final CandidatePrivacySettingsService settings;
    private final PersonActivityRecorder activity;
    private final AuditService audit;

    public boolean isOffered() {
        DataProtectionSettings dataProtection = properties.dataProtection();
        return dataProtection != null && dataProtection.enabled();
    }

    /** Null when data protection is off: the email goes without a footer. */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutreachPrivacyFooter footerFor(UUID workspaceId, UUID personId, UUID senderUserId, Instant now) {
        if (!isOffered() || personId == null) {
            return null;
        }
        Person person = people.requireInWorkspace(personId, workspaceId);
        CandidatePrivacySettings firm = settings.settingsOf(workspaceId);
        String token = privacyLinks.mint(workspaceId, personId, PrivacyLinkPurpose.OPT_OUT, senderUserId, now,
                OPT_OUT_LINK_LIFETIME).token();
        String baseUrl = properties.web().baseUrl();
        String page = baseUrl + "/privacy/" + token;
        String notice = firm.noticeUrl() == null ? page : firm.noticeUrl();
        String html = templates.buildOutreachPrivacyFooterHtml(statementOf(person, firm), notice,
                page + "?action=opt-out");
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

    private static String statementOf(Person person, CandidatePrivacySettings firm) {
        String holder = firm.controllerName() == null ? "We hold" : firm.controllerName() + " holds";
        return holder + " your professional details, which came from "
                + PrivacyNoticeWording.sourceOf(person.getSource()) + ".";
    }
}
