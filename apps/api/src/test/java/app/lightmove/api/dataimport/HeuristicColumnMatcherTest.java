package app.lightmove.api.dataimport;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.customcolumn.constant.CustomColumnTarget;
import app.lightmove.api.customcolumn.constant.CustomColumnType;
import app.lightmove.api.customcolumn.dto.CustomColumnDto;
import app.lightmove.api.dataimport.constant.ImportTargetField;
import app.lightmove.api.dataimport.model.ColumnMapping;
import app.lightmove.api.dataimport.model.ParsedSheet;
import app.lightmove.api.dataimport.model.SheetColumn;
import app.lightmove.api.dataimport.service.HeuristicColumnMatcher;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The mapping that works with no credentials at all.
 *
 * <p>This is not a nice-to-have: Vertex needs Application Default Credentials on every path including
 * a plain local run, so for many users this is the only mapper that ever runs. If it stops matching
 * "Organisation" to a company name, the import stops working for them and the model tests say nothing
 * about it.
 */
class HeuristicColumnMatcherTest {

    private final HeuristicColumnMatcher matcher = new HeuristicColumnMatcher();

    @ParameterizedTest
    @DisplayName("matches the header spellings consultants' files actually carry")
    @CsvSource({
            "Company,COMPANY_NAME",
            "Company Name,COMPANY_NAME",
            "Organisation,COMPANY_NAME",
            "Organization,COMPANY_NAME",
            "Employer,COMPANY_NAME",
            "E-Mail,CANDIDATE_EMAIL",
            "e_mail,CANDIDATE_EMAIL",
            "Work Email,CANDIDATE_EMAIL",
            "Email Address,CANDIDATE_EMAIL",
            "Full Name,CANDIDATE_NAME",
            "Job Title,CANDIDATE_TITLE",
            "Headcount,COMPANY_EMPLOYEES",
            "No of Employees,COMPANY_EMPLOYEES",
            "Turnover,COMPANY_REVENUE",
            "Seniority Level,CANDIDATE_SENIORITY",
            "LinkedIn URL,CANDIDATE_LINKEDIN",
            "Notice Period,CANDIDATE_NOTICE_PERIOD",
    })
    void matchesRealHeaders(String header, ImportTargetField expected) {
        assertThat(matcher.match(header))
                .hasValueSatisfying(match -> assertThat(match.field()).isEqualTo(expected));
    }

    @Test
    @DisplayName("a header nothing recognises does not match a field by accident")
    void refusesAWeakMatch() {
        assertThat(matcher.match("Ethnicity")).isEmpty();
        assertThat(matcher.match("Client Ranking")).isEmpty();
    }

    @Test
    @DisplayName("an unrecognised header becomes a custom column rather than being dropped")
    void keepsAnUnrecognisedColumn() {
        ParsedSheet sheet = sheetOf(
                column(0, "Company", SheetColumn.ValueShape.SHORT_TEXT),
                column(1, "Ethnicity", SheetColumn.ValueShape.SHORT_TEXT));

        List<ColumnMapping> mappings = matcher.propose(sheet, List.of()).mappings();

        assertThat(mappings.get(0).field()).isEqualTo(ImportTargetField.COMPANY_NAME);
        assertThat(mappings.get(1).isCustom()).isTrue();
        assertThat(mappings.get(1).customLabel()).isEqualTo("Ethnicity");
        assertThat(mappings.get(1).customFieldKey()).isNull();
    }

    @Test
    @DisplayName("an unrecognised header the mandate already has a column for fills that column")
    void reusesAnExistingCustomColumn() {
        // What makes importing an updated copy of the same file top up the Ethnicity column rather
        // than asking to make a second one beside it.
        ParsedSheet sheet = sheetOf(column(0, "Ethnicity", SheetColumn.ValueShape.SHORT_TEXT));
        CustomColumnDto existing = new CustomColumnDto("id", "candidate", "ethnicity", "Ethnicity",
                "text", 0, false);

        List<ColumnMapping> mappings = matcher.propose(sheet, List.of(existing)).mappings();

        assertThat(mappings.getFirst().customFieldKey()).isEqualTo("ethnicity");
        assertThat(mappings.getFirst().customLabel()).isEqualTo("Ethnicity");
    }

    @Test
    @DisplayName("two headers cannot claim one field; the second keeps its data as a custom column")
    void refusesToMapTwoHeadersOntoOneField() {
        // Otherwise the second column silently overwrites the first on every row and the import
        // reports success.
        ParsedSheet sheet = sheetOf(
                column(0, "Email", SheetColumn.ValueShape.EMAIL),
                column(1, "Work Email", SheetColumn.ValueShape.EMAIL));

        List<ColumnMapping> mappings = matcher.propose(sheet, List.of()).mappings();

        assertThat(mappings.get(0).field()).isEqualTo(ImportTargetField.CANDIDATE_EMAIL);
        assertThat(mappings.get(1).isCustom()).isTrue();
    }

    @Test
    @DisplayName("a new custom column is typed from what its values look like")
    void typesANewColumnFromItsValues() {
        ParsedSheet sheet = sheetOf(
                column(0, "Client Ranking", SheetColumn.ValueShape.NUMBER),
                column(1, "Interviewed", SheetColumn.ValueShape.BOOLEAN),
                column(2, "Ethnicity", SheetColumn.ValueShape.SHORT_TEXT));

        List<ColumnMapping> mappings = matcher.propose(sheet, List.of()).mappings();

        assertThat(mappings.get(0).customType()).isEqualTo(CustomColumnType.NUMBER);
        assertThat(mappings.get(1).customType()).isEqualTo(CustomColumnType.BOOLEAN);
        assertThat(mappings.get(2).customType()).isEqualTo(CustomColumnType.TEXT);
    }

    @Test
    @DisplayName("a company-shaped header lands its custom column on the company, not the person")
    void aimsACustomColumnAtTheRightHalfOfTheRow() {
        ParsedSheet sheet = sheetOf(
                column(0, "Company Ownership", SheetColumn.ValueShape.SHORT_TEXT),
                column(1, "Ethnicity", SheetColumn.ValueShape.SHORT_TEXT));

        List<ColumnMapping> mappings = matcher.propose(sheet, List.of()).mappings();

        assertThat(mappings.get(0).customColumnTarget()).isEqualTo(CustomColumnTarget.COMPANY);
        assertThat(mappings.get(1).customColumnTarget()).isEqualTo(CustomColumnTarget.CANDIDATE);
    }

    @Test
    @DisplayName("a known spelling is certain; a fuzzy hit is only likely")
    void separatesAKnownSpellingFromAGuess() {
        // The distinction that decides whether the model is paid for at all.
        assertThat(matcher.match("Company Name")).hasValueSatisfying(
                match -> assertThat(match.certain()).isTrue());
        assertThat(matcher.match("Legal Company Name"))
                .hasValueSatisfying(match -> assertThat(match.certain()).isFalse());
    }

    @Test
    @DisplayName("a sheet of known headers reports itself as certain throughout")
    void reportsAnAllKnownSheetAsCertain() {
        assertThat(matcher.propose(sheetOf(
                column(0, "Company", SheetColumn.ValueShape.SHORT_TEXT),
                column(1, "Email", SheetColumn.ValueShape.EMAIL)), List.of()).everyColumnCertain())
                .isTrue();
    }

    @Test
    @DisplayName("an unrecognised header makes the sheet uncertain, however well the rest matched")
    void oneUnknownHeaderMakesTheSheetUncertain() {
        // It may be a field we hold under a name we do not know, which is the model's job to spot.
        assertThat(matcher.propose(sheetOf(
                column(0, "Company", SheetColumn.ValueShape.SHORT_TEXT),
                column(1, "Ethnicity", SheetColumn.ValueShape.SHORT_TEXT)), List.of()).everyColumnCertain())
                .isFalse();
    }

    @Test
    @DisplayName("a header naming a column this project already has is certain")
    void anExistingCustomColumnIsCertain() {
        CustomColumnDto ethnicity =
                new CustomColumnDto("c1", "candidate", "ethnicity", "Ethnicity", "text", 0, false);

        assertThat(matcher.propose(sheetOf(
                column(0, "Company", SheetColumn.ValueShape.SHORT_TEXT),
                column(1, "Ethnicity", SheetColumn.ValueShape.SHORT_TEXT)),
                List.of(ethnicity)).everyColumnCertain())
                .isTrue();
    }

    private static SheetColumn column(int index, String header, SheetColumn.ValueShape shape) {
        return new SheetColumn(index, header, shape, List.of(), false);
    }

    private static ParsedSheet sheetOf(SheetColumn... columns) {
        return new ParsedSheet(List.of(columns), List.of());
    }
}
