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

/**
 * Every column of the Companies grid that carries data, in the order the grid draws them.
 *
 * <p>The headers are the grid's own, so a file and the screen read the same way. Two grid columns are
 * deliberately absent: {@code actions}, which is buttons, and {@code links} — which <i>is</i> data,
 * merely drawn as two icons, so it is expanded here into {@link #WEBSITE} and
 * {@link #COMPANY_LINKEDIN} rather than dropped.
 *
 * <p>Figures leave unformatted. The grid prints a revenue as {@code $1.2B}, which is right on a
 * screen and useless in a column a spreadsheet is asked to sum.
 */
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

    /**
     * ISO-8601, in UTC. A spreadsheet reads it as a date whatever locale opens the file, which a
     * rendered "16 Sep 2026" does not.
     */
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

    /** Every value in one cell: the grid shows the first and a {@code +N}, a file has no such excuse. */
    private static final String CONTACT_SEPARATOR = "; ";

    private final String header;
    private final Function<ExportRow, String> extractor;

    ExportColumn(String header, Function<ExportRow, String> extractor) {
        this.header = header;
        this.extractor = extractor;
    }

    /** The column's heading, which is the grid's own heading for it. */
    public String header() {
        return header;
    }

    public String valueOf(ExportRow row) {
        return text(extractor.apply(row));
    }

    public static List<ExportColumn> all() {
        return List.of(values());
    }

    /**
     * Where a grid line is, taken as a unit.
     *
     * <p>Ported from the grid's own {@code rowLocation}: a line is a person at a company and the
     * person is what it is about, so the executive's own location wins whole and the company's HQ is
     * what an empty slot falls back to. Field by field, a profile that recorded only a city would
     * borrow its employer's country and read as somewhere neither of them is.
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
     * The labels the grid's badges carry, not the wire tokens. Nobody wants {@code notInterested} in
     * a file they send a client. A deliberate second copy of the SPA's own vocabulary
     * ({@code candidateVocabulary.ts}, {@code triageVocabulary.ts}) — rename one, rename both.
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
            case WEB -> "AI Research";
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
