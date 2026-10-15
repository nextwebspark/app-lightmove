package app.lightmove.api.enrichment.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContactRedactionTest {

    @Test
    @DisplayName("emails and phone numbers, however they are written, never reach a model")
    void contactsAreTakenOut() {
        String redacted = ContactRedaction.redact("""
                Email layla.mansour+cv@gulf-dairy.ae, mobile +971 50 555 0101, office (04) 321 6543,
                KSA 0555123456, US (212) 555-0101.""");

        assertThat(redacted).doesNotContain("layla", "555", "321", "0555123456")
                .contains("Email " + ContactRedaction.EMAIL_REMOVED, "mobile " + ContactRedaction.PHONE_REMOVED);
    }

    @Test
    @DisplayName("dates, years and money stay: they are what an assessment reads")
    void careerFactsStay() {
        String text = "CFO 2019-2024, VP 2014 - 2019, joined 01/02/2014, revenue AED 12 500 000, base 1,250,000.";

        assertThat(ContactRedaction.redact(text)).isEqualTo(text);
    }
}
