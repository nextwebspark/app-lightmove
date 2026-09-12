package app.lightmove.api.position;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.position.constant.ExtractionSource;
import app.lightmove.api.position.constant.ProposalConfidence;
import app.lightmove.api.position.model.ExtractedField;
import app.lightmove.api.position.model.ProposedPositionDetails;
import app.lightmove.api.position.service.HeuristicBriefReader;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The deterministic fallback, run over synthetic text shaped like the real fixtures' own structure —
 * a colon- or wide-gap-separated header block, a bulleted or sentence-led responsibilities section,
 * and the employment-type/seniority keyword rules — rather than tied to one PDF's exact byte-for-byte
 * extraction, which a font substitution or a page break can change without any of these rules being
 * wrong. {@code PositionExtractionIntegrationTest} exercises the real fixtures end to end.
 *
 * <p>A real {@link IntegrationTest} context rather than a hand-built one: the seniority rule reuses
 * {@code PositionTemplateService}'s seeded catalog, and reconstructing that catalog by hand here would
 * test a fake of it rather than the real curated mapping this rule exists to avoid duplicating.
 */
@IntegrationTest
class HeuristicBriefReaderTest {

    @Autowired HeuristicBriefReader reader;

    @Test
    @DisplayName("a colon-separated header block yields title, location and department")
    void readsAColonSeparatedHeaderBlock() {
        ProposedPositionDetails proposed = reader.propose(UUID.randomUUID(), """
                Job Title: Chief Financial Officer
                Location: Dubai, UAE
                Department: Group Finance
                """);

        assertThat(proposed.source()).isEqualTo(ExtractionSource.DOCUMENT_HEADINGS);
        assertThat(valueOf(proposed, "roleTitle")).isEqualTo("Chief Financial Officer");
        assertThat(valueOf(proposed, "location")).isEqualTo("Dubai, UAE");
        assertThat(valueOf(proposed, "department")).isEqualTo("Group Finance");
    }

    @Test
    @DisplayName("a wide-gap table row, with no colon at all, yields the same fields")
    void readsAWideGapTableRow() {
        // The separator that survives a multi-column PDF's text extraction when no colon is drawn at
        // all: a run of two or more spaces standing in for the table's cell boundary.
        ProposedPositionDetails proposed = reader.propose(UUID.randomUUID(),
                "Job Title    Chief Financial Officer\n" + "Location     Dubai, UAE\n");

        assertThat(valueOf(proposed, "roleTitle")).isEqualTo("Chief Financial Officer");
        assertThat(valueOf(proposed, "location")).isEqualTo("Dubai, UAE");
    }

    @Test
    @DisplayName("a header row's value stops at the next column's wide gap")
    void headerValueStopsAtTheNextColumnGap() {
        ProposedPositionDetails proposed = reader.propose(UUID.randomUUID(),
                "Job Title    CEO                                              Department\n");

        assertThat(valueOf(proposed, "roleTitle")).isEqualTo("CEO");
    }

    @Test
    @DisplayName("a single space is not a header separator, so ordinary prose is left alone")
    void aSingleSpaceIsNotAHeaderSeparator() {
        ProposedPositionDetails proposed = reader.propose(UUID.randomUUID(),
                "Role of the finance function is critical to enterprise value.\n");

        assertThat(fieldNamed(proposed, "roleTitle")).isEmpty();
    }

    @Test
    @DisplayName("bulleted responsibilities under a sentence-style lead-in are read, each with a snippet")
    void readsBulletedResponsibilities() {
        ProposedPositionDetails proposed = reader.propose(UUID.randomUUID(), """
                Specifically, responsibilities include the following:
                - Lead the finance function across the group
                - Own the annual budget cycle
                - Chair the audit committee

                Ideal Candidate
                Something else entirely.
                """);

        List<ExtractedField> responsibilities = fieldsNamed(proposed, "responsibility");
        assertThat(responsibilities).hasSize(3);
        assertThat(responsibilities.get(0).value()).isEqualTo("Lead the finance function across the group");
        responsibilities.forEach(field -> assertThat(field.snippet()).isNotBlank());
    }

    @Test
    @DisplayName("a wrapped bullet's continuation line joins the item rather than starting a new one")
    void joinsAWrappedBulletsContinuationLine() {
        // No indentation distinguishes the two — the shape a real PDF's bullet list often extracts
        // as — so only the bullet glyph itself tells a new item from a continuation.
        ProposedPositionDetails proposed = reader.propose(UUID.randomUUID(), """
                Key Responsibilities
                • Lead and participate in the strategic planning
                process for the organization, along with monitoring
                • Approve key strategic and operating principles
                """);

        List<ExtractedField> responsibilities = fieldsNamed(proposed, "responsibility");
        assertThat(responsibilities).hasSize(2);
        assertThat(responsibilities.get(0).value())
                .isEqualTo("Lead and participate in the strategic planning process for the organization, "
                        + "along with monitoring");
    }

    @Test
    @DisplayName("every employment-type keyword resolves, and permanent outranks the word contract")
    void resolvesEveryEmploymentTypeKeyword() {
        assertThat(employmentTypeFor("This is a permanent contract based in Dubai."))
                .isEqualTo("FULL_TIME_PERMANENT");
        assertThat(employmentTypeFor("A fixed-term contract for 12 months.")).isEqualTo("FIXED_TERM_CONTRACT");
        assertThat(employmentTypeFor("This is a part-time role.")).isEqualTo("PART_TIME");
        assertThat(employmentTypeFor("An interim mandate for 6 months.")).isEqualTo("INTERIM");
        assertThat(employmentTypeFor("A retained advisory engagement.")).isEqualTo("RETAINED_ADVISORY");
    }

    @Test
    @DisplayName("a recognised title reaches its own template's seniority, at medium confidence")
    void recognisedTitleReachesItsTemplatesSeniority() {
        ProposedPositionDetails proposed = reader.propose(UUID.randomUUID(),
                "Job Title: Chief Financial Officer\n");

        ExtractedField seniority = fieldNamed(proposed, "seniority").orElseThrow();
        assertThat(seniority.value()).isEqualTo("C_SUITE");
        assertThat(seniority.confidence()).isEqualTo(ProposalConfidence.MEDIUM);
    }

    @Test
    @DisplayName("an unrecognised title falls to the generic template, at low confidence")
    void unrecognisedTitleFallsToTheGenericTemplateAtLowConfidence() {
        ProposedPositionDetails proposed = reader.propose(UUID.randomUUID(),
                "Job Title: Head of Alchemy\n");

        ExtractedField seniority = fieldNamed(proposed, "seniority").orElseThrow();
        assertThat(seniority.confidence()).isEqualTo(ProposalConfidence.LOW);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Optional<ExtractedField> fieldNamed(ProposedPositionDetails proposed, String key) {
        return proposed.fields().stream().filter(field -> field.fieldKey().equals(key)).findFirst();
    }

    private static List<ExtractedField> fieldsNamed(ProposedPositionDetails proposed, String key) {
        return proposed.fields().stream().filter(field -> field.fieldKey().equals(key)).toList();
    }

    private static String valueOf(ProposedPositionDetails proposed, String key) {
        return fieldNamed(proposed, key).map(ExtractedField::value).orElse(null);
    }

    private String employmentTypeFor(String text) {
        return valueOf(reader.propose(UUID.randomUUID(), text), "employmentType");
    }
}
