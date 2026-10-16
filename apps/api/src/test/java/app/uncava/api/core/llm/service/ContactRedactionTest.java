package app.uncava.api.core.llm.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContactRedactionTest {

    @Test
    @DisplayName("emails and phone numbers, however they are written, never reach a model")
    void contactsAreTakenOut() {
        String redacted = ContactRedaction.redact("""
                Email layla.mansour+cv@gulf-dairy.ae, mobile +971 50 555 0101, office (04) 321 6543,
                KSA 0555123456, US (212) 555-0101, Doha Mobile 5512 3456.""");

        assertThat(redacted).doesNotContain("layla", "555", "321", "0555123456", "5512")
                .contains("Email " + ContactRedaction.EMAIL_REMOVED, "mobile " + ContactRedaction.PHONE_REMOVED,
                        "Mobile " + ContactRedaction.PHONE_REMOVED);
    }

    @Test
    @DisplayName("a phone ending one line is taken out, and the dates opening the next stay")
    void aLineBreakNeverJoinsAPhoneToADate() {
        assertThat(ContactRedaction.redact("+971 50 123 4567\n2015 - 2019 Group CFO"))
                .isEqualTo(ContactRedaction.PHONE_REMOVED + "\n2015 - 2019 Group CFO");
    }

    @Test
    @DisplayName("dates, ranges and money stay: they are what an assessment reads")
    void careerFactsStay() {
        String text = """
                CFO 2019-2024, VP 2014 - 2019, joined 01/02/2014, 09.2012 - 05.2016, 2012-09 - 2016-05,
                01-02-2014 - 03-04-2016, revenue AED 1 250 000 000, base 1,250,000, staff cost 12 500 000.""";

        assertThat(ContactRedaction.redact(text)).isEqualTo(text);
    }
}
