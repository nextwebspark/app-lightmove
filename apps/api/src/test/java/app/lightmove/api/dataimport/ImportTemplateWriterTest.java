package app.lightmove.api.dataimport;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.customcolumn.dto.CustomColumnDto;
import app.lightmove.api.dataimport.model.HeaderMatch;
import app.lightmove.api.dataimport.service.HeuristicColumnMatcher;
import app.lightmove.api.dataimport.service.ImportTemplateWriter;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The blank CSV a consultant downloads, fills in and uploads back. */
class ImportTemplateWriterTest {

    private final ImportTemplateWriter writer = new ImportTemplateWriter();
    private final HeuristicColumnMatcher matcher = new HeuristicColumnMatcher();

    @Test
    @DisplayName("every header it writes is one the matcher knows for certain")
    void everyHeaderMatchesWithCertainty() {
        // The property the whole optimisation rests on: a file built from this template maps without a
        // model call. A label edited out of step with the synonym table would quietly cost one call per
        // import, and nothing else in the suite would notice.
        for (String header : headersOf(writer.templateFor(List.of()))) {
            assertThat(matcher.match(header))
                    .as("header %s", header)
                    .hasValueSatisfying(match -> assertThat(match.certain()).isTrue());
        }
    }

    @Test
    @DisplayName("carries this mandate's own columns, so a second import stays free too")
    void includesTheProjectsCustomColumns() {
        String template = writer.templateFor(List.of(
                new CustomColumnDto("c1", "candidate", "ethnicity", "Ethnicity", "text", 0, false)));

        assertThat(headersOf(template)).contains("Ethnicity");
    }

    @Test
    @DisplayName("leaves a hidden column out")
    void skipsHiddenColumns() {
        String template = writer.templateFor(List.of(
                new CustomColumnDto("c1", "candidate", "ethnicity", "Ethnicity", "text", 0, true)));

        assertThat(headersOf(template)).doesNotContain("Ethnicity");
    }

    @Test
    @DisplayName("shows one filled row rather than describing the formats")
    void carriesAnExampleRow() {
        List<String> lines = writer.templateFor(List.of()).lines().toList();

        assertThat(lines).hasSize(2);
        assertThat(lines.get(1)).contains("ACWA Power").contains("Layla Haddad");
    }

    @Test
    @DisplayName("quotes a value holding a comma, so Excel does not split it into two columns")
    void quotesWhereItMust() {
        String template = writer.templateFor(List.of(
                new CustomColumnDto("c1", "candidate", "rank", "Rank, internal", "text", 0, false)));

        assertThat(template).contains("\"Rank, internal\"");
        assertThat(headersOf(template)).contains("Rank, internal");
    }

    /** Splits the header line the way a CSV reader would, honouring the quoting above. */
    private static List<String> headersOf(String template) {
        String header = template.lines().findFirst().orElseThrow();
        return Arrays.stream(header.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"))
                .map(value -> value.startsWith("\"") && value.endsWith("\"")
                        ? value.substring(1, value.length() - 1).replace("\"\"", "\"")
                        : value)
                .toList();
    }
}
