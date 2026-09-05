package app.lightmove.api.dataimport.service;

import app.lightmove.api.customcolumn.dto.CustomColumnDto;
import app.lightmove.api.dataimport.constant.ImportTargetField;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Builds the blank CSV a consultant can download, fill in and upload back.
 *
 * <p>Optional, never required — the import maps whatever headers a file arrives with. What the
 * template buys is that a file built from it maps with no guessing at all: every header here is a
 * spelling {@link HeuristicColumnMatcher} matches with certainty.
 * {@code ImportTemplateWriterTest} pins that property, because a label edited out of step with the
 * synonym table would quietly turn every template import into a mapping somebody has to correct.
 *
 * <p>A dozen fields rather than all thirty-one: a sheet wide enough to hold every field is a sheet
 * most of whose columns come back empty, and each empty one is another to scroll past. The rest stay
 * importable — they are just not pre-drawn.
 */
@Service
public class ImportTemplateWriter {

    /** The fields people actually fill in, in the order a row reads: the company, then the person. */
    private static final List<ImportTargetField> COMMON_FIELDS = List.of(
            ImportTargetField.COMPANY_NAME,
            ImportTargetField.COMPANY_INDUSTRY,
            ImportTargetField.COMPANY_COUNTRY,
            ImportTargetField.COMPANY_CITY,
            ImportTargetField.COMPANY_EMPLOYEES,
            ImportTargetField.COMPANY_WEBSITE,
            ImportTargetField.CANDIDATE_NAME,
            ImportTargetField.CANDIDATE_TITLE,
            ImportTargetField.CANDIDATE_SENIORITY,
            ImportTargetField.CANDIDATE_EMAIL,
            ImportTargetField.CANDIDATE_PHONE,
            ImportTargetField.CANDIDATE_LINKEDIN);

    /** One filled row, so the shape of a value is shown rather than described. */
    private static final Map<ImportTargetField, String> EXAMPLE_ROW = Map.ofEntries(
            Map.entry(ImportTargetField.COMPANY_NAME, "ACWA Power"),
            Map.entry(ImportTargetField.COMPANY_INDUSTRY, "Oil & Energy"),
            Map.entry(ImportTargetField.COMPANY_COUNTRY, "Saudi Arabia"),
            Map.entry(ImportTargetField.COMPANY_CITY, "Riyadh"),
            Map.entry(ImportTargetField.COMPANY_EMPLOYEES, "3000"),
            Map.entry(ImportTargetField.COMPANY_WEBSITE, "https://acwapower.com"),
            Map.entry(ImportTargetField.CANDIDATE_NAME, "Layla Haddad"),
            Map.entry(ImportTargetField.CANDIDATE_TITLE, "Chief Financial Officer"),
            Map.entry(ImportTargetField.CANDIDATE_SENIORITY, "C-Suite"),
            Map.entry(ImportTargetField.CANDIDATE_EMAIL, "layla.haddad@example.com"),
            Map.entry(ImportTargetField.CANDIDATE_PHONE, "+966 50 123 4567"),
            Map.entry(ImportTargetField.CANDIDATE_LINKEDIN, "https://linkedin.com/in/example"));

    public static final String FILE_NAME = "lightmove-import-template.csv";

    /**
     * The template for one mandate.
     *
     * <p>Project-scoped because the mandate's own custom columns are appended: without them a second
     * import of the same shape would meet an unrecognised header and be guessed at again to be told
     * what it already knew.
     */
    public String templateFor(List<CustomColumnDto> customColumns) {
        List<String> headers = new ArrayList<>(COMMON_FIELDS.stream().map(ImportTargetField::label).toList());
        List<String> example = new ArrayList<>(COMMON_FIELDS.stream().map(EXAMPLE_ROW::get).toList());
        customColumns.stream()
                .filter(column -> !column.hidden())
                .forEach(column -> {
                    headers.add(column.label());
                    example.add("");
                });
        return row(headers) + "\r\n" + row(example) + "\r\n";
    }

    /**
     * CRLF and RFC 4180 quoting, because this file is opened in Excel far more often than by a parser
     * — a header carrying a comma is otherwise two columns the moment it is saved and sent back.
     */
    private static String row(List<String> values) {
        return String.join(",", values.stream().map(ImportTemplateWriter::quoted).toList());
    }

    private static String quoted(String value) {
        String safe = value == null ? "" : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return '"' + safe.replace("\"", "\"\"") + '"';
        }
        return safe;
    }
}
