package app.lightmove.api.core.email.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.WebSettings;
import app.lightmove.api.core.email.constant.EmailSenderIdentity;
import app.lightmove.api.core.email.constant.PrivacyRequestEmailKind;
import app.lightmove.api.core.email.model.EmailMessage;
import app.lightmove.api.core.security.TestAuthSettings;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/** Each privacy mail type leaves from the privacy sending address, and Uncava's own mail never does (#1493, B15). */
class EmailSenderIdentityTest {

    private static final String BASE_URL = "https://beta.uncava.com";
    private static final String LINK = BASE_URL + "/privacy/abc123";
    private static final String NOTICE = "https://firm.example/privacy";
    private static final String FIRM = "Meridian Search";
    private static final String REPLY_TO = "privacy@firm.example";

    private final EmailTemplates templates = new EmailTemplates(new LightMoveProperties(
            TestAuthSettings.production().auth(), null, new WebSettings(BASE_URL, List.of(), "/auth/callback", 0, true, DataSize.ofMegabytes(2)),
            null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));

    @Test
    @DisplayName("every notice, consent request, share request, renewal and confirmation is privacy mail")
    void privacyMailTypesPickThePrivacySender() {
        List<EmailMessage> privacyMail = List.of(
                templates.buildPrivacyNoticeEmail("dana@x.ae", FIRM, REPLY_TO, "LinkedIn", "legitimate interests",
                        LINK, NOTICE),
                templates.buildConsentRequestEmail("dana@x.ae", "Dana Haddad", FIRM, REPLY_TO, "LinkedIn",
                        List.of("keep your details on file for future roles"), LINK, NOTICE, "12 December 2026"),
                templates.buildShareConsentRequestEmail("dana@x.ae", "Dana Haddad", FIRM, REPLY_TO, "LinkedIn",
                        "Group CFO", null, LINK, NOTICE, "12 December 2026"),
                templates.buildConsentRenewalEmail("dana@x.ae", "Dana Haddad", FIRM, REPLY_TO, "1 December 2024",
                        "1 December 2026", 24, LINK, NOTICE, "1 December 2026"),
                templates.buildConsentConfirmationEmail("dana@x.ae", "Dana Haddad", FIRM, REPLY_TO,
                        "26 November 2026", List.of("Keep on file: yes"), LINK, "26 November 2027", NOTICE));

        assertThat(privacyMail).allSatisfy(message -> {
            assertThat(message.senderIdentity()).isEqualTo(EmailSenderIdentity.PRIVACY);
            assertThat(message.senderName()).isEqualTo(FIRM);
            assertThat(message.replyTo()).isEqualTo(REPLY_TO);
        });
    }

    @Test
    @DisplayName("sign-in, invitation, admin alerts and the sub-processor notice stay on the account sender")
    void accountMailStaysOnTheAccountSender() {
        List<EmailMessage> accountMail = List.of(
                templates.buildVerificationEmail("nadia@x.ae", "Nadia Rahman", LINK),
                templates.buildPasswordResetEmail("nadia@x.ae", "Nadia Rahman", LINK),
                templates.buildInvitationEmail("nadia@x.ae", "Alok Sharma", "Meridian", "MEMBER", LINK),
                templates.buildPrivacyRequestEmail("nadia@x.ae", "Nadia Rahman", "Dana Haddad",
                        PrivacyRequestEmailKind.OBJECTION, LINK),
                templates.buildObjectionRecordedEmail("nadia@x.ae", "Nadia Rahman", "Alok Sharma", "Dana Haddad",
                        LINK),
                templates.buildSubprocessorNoticeEmail("nadia@x.ae", "Nadia Rahman", "Meridian", "Acme Mail",
                        "Email addresses", "Delivery", "United States", "1 January 2027", LINK, LINK));

        assertThat(accountMail).allSatisfy(message -> {
            assertThat(message.senderIdentity()).isEqualTo(EmailSenderIdentity.ACCOUNT);
            assertThat(message.senderName()).isNull();
        });
    }
}
