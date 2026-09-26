package app.lightmove.api.dataexport.constant;

import app.lightmove.api.candidate.constant.CandidateStatus;
import app.lightmove.api.candidate.dto.CandidateEmailDto;
import app.lightmove.api.candidate.dto.CandidatePhoneDto;
import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.dataexport.model.ExportRow;
import app.lightmove.api.triagecompany.constant.TriageCompanySource;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Every data column of the Companies grid, in grid order, under the grid's headers. {@code links} is
 * expanded into {@link #WEBSITE} and {@link #COMPANY_LINKEDIN}; figures leave unformatted so a
 * spreadsheet can sum them.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ExportColumn {

    COMPANY("Company", row -> row.company() != null
            ? row.company().companyName()
            : row.candidate().companyName()),

    WEBSITE("Website", row -> company(row, TriageCompanyResponse::website)),

    COMPANY_LINKEDIN("Company LinkedIn", row -> company(row, TriageCompanyResponse::companyLinkedinUrl)),

    COUNTRY("Country", row -> location(row).country()),

    EXECUTIVE("Executive", row -> candidate(row, CandidateResponse::fullName)),

    TITLE("Title", row -> candidate(row, CandidateResponse::title)),

    EMAIL("Email", row -> row.candidate() == null ? ""
            : joined(row.candidate().contacts().emails().stream().map(CandidateEmailDto::address))),

    PHONE("Phone", row -> row.candidate() == null ? ""
            : joined(row.candidate().contacts().phones().stream().map(CandidatePhoneDto::number))),

    STATUS("Status", row -> row.candidate() == null ? "" : statusLabel(row.candidate().status())),

    SECTOR("Sector", row -> company(row, TriageCompanyResponse::industry)),

    CITY("City", row -> location(row).city()),

    REVENUE("Revenue", row -> company(row, response -> asString(response.annualRevenue()))),

    EMPLOYEES("Employees", row -> company(row, response -> asString(response.numEmployees()))),

    NOTE("Note", row -> company(row, TriageCompanyResponse::note)),

    FOUNDED("Founded", row -> company(row, response -> asString(response.foundedYear()))),

    ADDED("Added", row -> asDate(row.company() != null && row.company().addedAt() != null
            ? row.company().addedAt()
            : (row.candidate() == null ? null : row.candidate().addedAt()))),

    DESCRIPTION("Description", row -> company(row, TriageCompanyResponse::shortDescription)),

    SOURCE("Source", row -> row.company() == null ? "" : sourceLabel(row.company().source()));

    /** ISO-8601 UTC, which a spreadsheet reads as a date in any locale. */
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

    /** Every value in one cell, where the grid shows the first and a {@code +N}. */
    private static final String CONTACT_SEPARATOR = "; ";

    private final String header;
    private final Function<ExportRow, String> extractor;

    public String valueOf(ExportRow row) {
        return text(extractor.apply(row));
    }

    public static List<ExportColumn> all() {
        return List.of(values());
    }

    /**
     * The executive's own location wins whole, HQ only filling an empty slot — field by field, a city
     * alone would borrow its employer's country (ported from the grid's {@code rowLocation}).
     */
    private static Place location(ExportRow row) {
        CandidateResponse candidate = row.candidate();
        if (candidate != null && (has(candidate.locationCountry()) || has(candidate.locationCity()))) {
            return new Place(text(candidate.locationCountry()), text(candidate.locationCity()));
        }
        TriageCompanyResponse company = row.company();
        return company == null
                ? new Place("", "")
                : new Place(text(company.companyCountry()), text(company.companyCity()));
    }

    private record Place(String country, String city) {}

    /**
     * The badges' labels, not wire tokens. A deliberate copy of {@code candidateVocabulary.ts} and
     * {@code triageVocabulary.ts} — rename one, rename both.
     */
    private static String statusLabel(String wireToken) {
        CandidateStatus status = CandidateStatus.fromValue(wireToken);
        if (status == null) {
            return text(wireToken);
        }
        return switch (status) {
            case IDENTIFIED -> "Identified";
            case CONTACTED -> "Contacted";
            case ENGAGED -> "Engaged";
            case INTERESTED -> "Interested";
            case NOT_INTERESTED -> "Not interested";
            case OFF_LIMITS -> "Off-limits";
            case OUT_OF_SCOPE -> "Out of scope";
        };
    }

    private static String sourceLabel(String wireToken) {
        TriageCompanySource source = TriageCompanySource.fromValue(wireToken);
        if (source == null) {
            return text(wireToken);
        }
        return switch (source) {
            case STRATEGY -> "Strategy";
            case MANUAL -> "Manual";
            case EXTENSION -> "Plugin";
            case CSV -> "Import";
            case ASSISTANT -> "Assistant";
        };
    }

    private static String company(ExportRow row, Function<TriageCompanyResponse, String> read) {
        return row.company() == null ? "" : text(read.apply(row.company()));
    }

    private static String candidate(ExportRow row, Function<CandidateResponse, String> read) {
        return row.candidate() == null ? "" : text(read.apply(row.candidate()));
    }

    private static String joined(Stream<String> values) {
        return String.join(CONTACT_SEPARATOR, values.filter(ExportColumn::has).toList());
    }

    private static String asDate(Instant instant) {
        return instant == null ? "" : DATE.format(instant);
    }

    private static String asString(Number value) {
        return value == null ? "" : value.toString();
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static boolean has(String value) {
        return value != null && !value.isBlank();
    }
}
