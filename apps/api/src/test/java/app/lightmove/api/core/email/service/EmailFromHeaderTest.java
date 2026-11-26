package app.lightmove.api.core.email.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.config.EmailSettings;
import app.lightmove.api.core.email.constant.EmailSenderIdentity;
import app.lightmove.api.core.email.model.EmailMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmailFromHeaderTest {

    private static final EmailMessage ACCOUNT_MAIL = new EmailMessage("nadia@x.ae", "Reset", "<p>x</p>", "x");
    private static final EmailMessage PRIVACY_MAIL = ACCOUNT_MAIL.onBehalfOf("Meridian Search",
            "privacy@firm.example", EmailSenderIdentity.PRIVACY);

    private final EmailFromHeader header = new EmailFromHeader(new EmailSettings("resend", "Uncava",
            "noreply@uncava.com", "noreply@privacy.uncava.com", null, null));

    @Test
    @DisplayName("privacy mail names the firm and leaves from the privacy subdomain")
    void privacyMailUsesThePrivacyAddress() {
        assertThat(header.of(PRIVACY_MAIL)).isEqualTo("\"Meridian Search via Uncava\" <noreply@privacy.uncava.com>");
    }

    @Test
    @DisplayName("account mail leaves from the account address")
    void accountMailUsesTheAccountAddress() {
        assertThat(header.of(ACCOUNT_MAIL)).isEqualTo("Uncava <noreply@uncava.com>");
    }

    @Test
    @DisplayName("with no privacy address set, privacy mail falls back to the account address")
    void blankPrivacyAddressFallsBack() {
        EmailFromHeader unset = new EmailFromHeader(new EmailSettings("resend", "Uncava", "noreply@uncava.com", " ",
                null, null));

        assertThat(unset.of(PRIVACY_MAIL)).isEqualTo("\"Meridian Search via Uncava\" <noreply@uncava.com>");
    }

    @Test
    @DisplayName("a firm name cannot end the display name early or start a header of its own")
    void firmNameIsSanitised() {
        EmailMessage hostile = ACCOUNT_MAIL.onBehalfOf("Evil\" <x@y>\r\nBcc: a@b", null, EmailSenderIdentity.PRIVACY);

        assertThat(header.of(hostile)).isEqualTo("\"Evil x@yBcc: a@b via Uncava\" <noreply@privacy.uncava.com>");
    }

    @Test
    @DisplayName("a backslash in a firm name is dropped, never left to start a quoted-pair")
    void backslashInFirmNameIsSanitised() {
        EmailMessage escaping = ACCOUNT_MAIL.onBehalfOf("Firm\\ Ltd\\", null, EmailSenderIdentity.PRIVACY);

        assertThat(header.of(escaping)).isEqualTo("\"Firm Ltd via Uncava\" <noreply@privacy.uncava.com>");
    }
}
