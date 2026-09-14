package app.lightmove.api.position;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.PositionExtractionSettings;
import app.lightmove.api.core.config.PositionSettings;
import app.lightmove.api.core.llm.service.TextPseudonymiser.Redaction;
import app.lightmove.api.core.llm.service.TextPseudonymiser;
import app.lightmove.api.position.service.PositionDocumentRedactor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The vocabulary a position description is redacted against, in isolation from the model call.
 * Company-name redaction is off throughout (it needs a real {@code ClientRepository} lookup, covered
 * elsewhere) — these tests are about the contact-detail patterns alone.
 */
class PositionDocumentRedactorTest {

    private final PositionDocumentRedactor redactor = redactorWith(true);

    @Test
    @DisplayName("a currency figure is not mistaken for a phone number")
    void currencyFigureSurvivesRedaction() {
        Redaction redaction = redactor.redact(
                "The package is AED 1,200,000 per annum, reviewed yearly.", null, null);

        assertThat(redaction.text()).contains("AED 1,200,000");
    }

    @Test
    @DisplayName("a genuine phone number never reaches the model")
    void genuinePhoneNumberIsRedacted() {
        // A single-line "block" carrying a genuine phone number is a contacts card of one line, so
        // the block sweep drops the line wholesale — it never reaches the patterns-based
        // pseudonymisation pass at all, but the number is gone from the output either way.
        Redaction redaction = redactor.redact(
                "Call the consultant on +971 50 123 4567 for details.", null, null);

        assertThat(redaction.text()).doesNotContain("123 4567").doesNotContain("+971");
    }

    @Test
    @DisplayName("a currency figure survives even when a genuine phone number elsewhere is redacted")
    void currencyFigureSurvivesAlongsideARedactedPhoneNumber() {
        // Two separate paragraphs, so the contact-bearing one's block sweep can't take the
        // compensation figure down with it.
        Redaction redaction = redactor.redact("""
                Compensation
                The package is AED 1,200,000 per annum, reviewed yearly.
                Additional benefits include health insurance.

                Contact
                Call the consultant on +971 50 123 4567 for details.
                """, null, null);

        assertThat(redaction.text()).contains("AED 1,200,000");
        assertThat(redaction.text()).doesNotContain("123 4567").doesNotContain("+971");
    }

    @Test
    @DisplayName("redaction output is stable across repeated runs on the same input")
    void redactionIsDeterministic() {
        String text = """
                Compensation
                The package is AED 1,200,000 per annum, reviewed yearly.
                Additional benefits include health insurance.

                See https://acwa.example/careers for the full brief.
                """;

        Redaction first = redactor.redact(text, null, null);
        Redaction second = redactor.redact(text, null, null);

        assertThat(first.text()).isEqualTo(second.text());
        assertThat(first.text()).contains("AED 1,200,000").contains("[[URL_1]]");
    }

    private static PositionDocumentRedactor redactorWith(boolean redactContactDetails) {
        PositionExtractionSettings settings =
                new PositionExtractionSettings(true, 40_000, 60, false, redactContactDetails);
        LightMoveProperties properties = new LightMoveProperties(null, null, null, null,
                new PositionSettings(null, settings, null), null, null, null, null, null, null, null);
        return new PositionDocumentRedactor(new TextPseudonymiser(), null, properties);
    }
}
