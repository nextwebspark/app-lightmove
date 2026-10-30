package app.lightmove.api.pairing.service;

import app.lightmove.api.candidate.dto.AddedByResponse;
import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.core.text.service.NaturalTextOrder;
import app.lightmove.api.customcolumn.constant.CustomColumnTarget;
import app.lightmove.api.customcolumn.constant.CustomColumnType;
import app.lightmove.api.customcolumn.dto.CustomColumnDto;
import app.lightmove.api.pairing.constant.StageLineSortField;
import app.lightmove.api.pairing.dto.StageLineResponse;
import app.lightmove.api.pairing.model.StageLineFacts;
import app.lightmove.api.pairing.model.StageLineSort;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * The order grid lines take under one column's sort, each value read off the line as its cell draws it. A blank sorts
 * last whichever way the column runs, and lines that tie fall back to company, then executive, then ids, so a page
 * boundary never moves between two reads of the same stage.
 */
public final class StageLineOrder {

    private static final Comparator<String> TEXT = NaturalTextOrder.INSTANCE;

    private static final List<DateTimeFormatter> DATE_SPELLINGS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d-M-uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d.M.uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH));

    private static final Comparator<StageLineResponse> TIE_BREAK =
            Comparator.comparing(StageLineOrder::companyNameOf, Comparator.nullsLast(TEXT))
                    .thenComparing(StageLineOrder::executiveNameOf, Comparator.nullsLast(TEXT))
                    .thenComparing(line -> line.company() == null ? null : line.company().id(),
                            Comparator.nullsLast(Comparator.<UUID>naturalOrder()))
                    .thenComparing(line -> line.candidate() == null ? null : line.candidate().id(),
                            Comparator.nullsLast(Comparator.<UUID>naturalOrder()));

    private StageLineOrder() {
    }

    public static Comparator<StageLineResponse> of(StageLineSort sort, StageLineFacts facts) {
        Comparator<StageLineResponse> primary = sort.customColumn() == null
                ? byField(sort.field(), sort.descending(), facts)
                : byCustomColumn(sort.customColumn(), sort.descending());
        return primary.thenComparing(TIE_BREAK);
    }

    private static Comparator<StageLineResponse> byField(StageLineSortField field, boolean descending,
                                                         StageLineFacts facts) {
        return switch (field) {
            case COMPANY -> text(StageLineOrder::companyNameOf, descending);
            case EXECUTIVE -> text(StageLineOrder::executiveNameOf, descending);
            case TITLE -> text(line -> line.candidate() == null ? null : line.candidate().title(), descending);
            case COUNTRY -> text(line -> placeOf(line).country(), descending);
            case CITY -> text(line -> placeOf(line).city(), descending);
            case STATUS -> value(line -> line.candidate() == null ? null
                    : facts.statusRanks().get(line.candidate().status()), descending);
            case SECTOR -> text(line -> line.company() == null ? null : line.company().industry(), descending);
            case REVENUE -> value(line -> line.company() == null ? null : line.company().annualRevenue(), descending);
            case EMPLOYEES -> value(line -> line.company() == null ? null : line.company().numEmployees(), descending);
            case FOUNDED -> value(line -> line.company() == null ? null : line.company().foundedYear(), descending);
            case ADDED -> value(line -> line.company() != null && line.company().addedAt() != null
                    ? line.company().addedAt()
                    : line.candidate() == null ? null : line.candidate().addedAt(), descending);
            case ADDED_BY -> text(line -> adderNameOf(line, facts.executiveAdders()), descending);
            case NOTES -> value(line -> line.company() == null ? null
                    : facts.noteCounts().getOrDefault(line.company().id(), 0), descending);
            case SOURCE -> text(line -> line.company() == null ? null : line.company().source(), descending);
        };
    }

    private static Comparator<StageLineResponse> byCustomColumn(CustomColumnDto column, boolean descending) {
        Function<StageLineResponse, String> stored = line -> customValueOf(line, column);
        CustomColumnType type = CustomColumnType.fromValue(column.dataType());
        if (type == CustomColumnType.NUMBER) {
            return value(line -> numberOrNull(stored.apply(line)), descending);
        }
        if (type == CustomColumnType.DATE) {
            return value(line -> dateOrNull(stored.apply(line)), descending);
        }
        return text(stored, descending);
    }

    private static Comparator<StageLineResponse> text(Function<StageLineResponse, String> key, boolean descending) {
        return Comparator.comparing((StageLineResponse line) -> blankToNull(key.apply(line)),
                Comparator.nullsLast(descending ? TEXT.reversed() : TEXT));
    }

    private static <T extends Comparable<? super T>> Comparator<StageLineResponse> value(
            Function<StageLineResponse, T> key, boolean descending) {
        Comparator<T> order = Comparator.naturalOrder();
        return Comparator.<StageLineResponse, T>comparing(key,
                Comparator.nullsLast(descending ? order.reversed() : order));
    }

    private static String companyNameOf(StageLineResponse line) {
        if (line.company() != null) {
            return blankToNull(line.company().companyName());
        }
        return line.candidate() == null ? null : blankToNull(line.candidate().companyName());
    }

    private static String executiveNameOf(StageLineResponse line) {
        return line.candidate() == null ? null : blankToNull(line.candidate().fullName());
    }

    /**
     * Where the line is, as the Country and City cells draw it: the executive's own place, else the company's
     * headquarters — taken as a pair, so a profile naming only a city never borrows its employer's country.
     */
    private static Place placeOf(StageLineResponse line) {
        CandidateResponse candidate = line.candidate();
        if (candidate != null && (blankToNull(candidate.locationCountry()) != null
                || blankToNull(candidate.locationCity()) != null)) {
            return new Place(candidate.locationCountry(), candidate.locationCity());
        }
        TriageCompanyResponse company = line.company();
        return company == null ? new Place(null, null) : new Place(company.companyCountry(), company.companyCity());
    }

    private static String adderNameOf(StageLineResponse line, Map<UUID, AddedByResponse> executiveAdders) {
        if (line.company() != null && blankToNull(line.company().addedByName()) != null) {
            return line.company().addedByName();
        }
        if (line.candidate() == null) {
            return null;
        }
        AddedByResponse adder = executiveAdders.get(line.candidate().id());
        return adder == null ? null : adder.name();
    }

    private static String customValueOf(StageLineResponse line, CustomColumnDto column) {
        Map<String, String> values = CustomColumnTarget.COMPANY.value().equals(column.target())
                ? line.company() == null ? null : line.company().customFields()
                : line.candidate() == null ? null : line.candidate().customFields();
        return values == null ? null : blankToNull(values.get(column.fieldKey()));
    }

    private static BigDecimal numberOrNull(String stored) {
        if (stored == null) {
            return null;
        }
        try {
            return new BigDecimal(stored.replaceAll("[,\\s]", ""));
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static LocalDate dateOrNull(String stored) {
        if (stored == null) {
            return null;
        }
        return DATE_SPELLINGS.stream()
                .map(spelling -> dateOrNull(stored.strip(), spelling))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static LocalDate dateOrNull(String stored, DateTimeFormatter spelling) {
        try {
            return LocalDate.parse(stored, spelling);
        } catch (DateTimeParseException otherSpelling) {
            return null;
        }
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }

    private record Place(String country, String city) {}
}
