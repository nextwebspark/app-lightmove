package app.lightmove.api.dataimport.service;

import app.lightmove.api.core.text.service.CsvFormatter;
import app.lightmove.api.customcolumn.dto.CustomColumnDto;
import app.lightmove.api.dataimport.constant.ImportTargetField;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * The downloadable blank CSV. Every header is a spelling {@link HeuristicColumnMatcher} knows for
 * certain, so a file built from it needs no model call ({@code ImportTemplateWriterTest} pins that).
 */
@Service
public class ImportTemplateWriter {

    /** The fields people actually fill in, company then person; the rest stay importable. */
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

    /** Appends the mandate's custom columns, so a second import of the same shape needs no model call. */
    public String templateFor(List<CustomColumnDto> customColumns) {
        List<String> headers = new ArrayList<>(COMMON_FIELDS.stream().map(ImportTargetField::label).toList());
        List<String> example = new ArrayList<>(COMMON_FIELDS.stream().map(EXAMPLE_ROW::get).toList());
        customColumns.stream()
                .filter(column -> !column.hidden())
                .forEach(column -> {
                    headers.add(column.label());
                    example.add("");
                });
        return CsvFormatter.row(headers) + "\r\n" + CsvFormatter.row(example) + "\r\n";
    }
}
