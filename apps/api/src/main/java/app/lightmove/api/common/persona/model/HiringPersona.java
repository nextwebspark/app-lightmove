package app.lightmove.api.common.persona.model;

import app.lightmove.api.common.industry.model.ResolvedIndustry;
import app.lightmove.api.common.industry.service.Industries;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * What a hiring company is, for the assistant to tailor research to: the in-house firm's own
 * ({@code app_lm_workspace.persona}) or an agency client's ({@code app_lm_client.persona}).
 */
public record HiringPersona(
        String summary,
        List<String> sectors,
        List<String> competitors,
        List<String> geographies,
        String notes
) {

    /** Mirrors the {@code @Size} cap on every list of {@code UpdateHiringPersonaRequest}. */
    public static final int MAX_LIST_ITEMS = 20;

    public HiringPersona {
        summary = blankToNull(summary);
        notes = blankToNull(notes);
        sectors = distinct(sectors);
        competitors = distinct(competitors);
        geographies = distinct(geographies);
    }

    public static HiringPersona empty() {
        return new HiringPersona(null, List.of(), List.of(), List.of(), null);
    }

    /** The starting point for a company picked from the universe: its industry, its sector and its country. */
    public static HiringPersona seededFrom(PersonaSeed company) {
        return empty().refiledFrom(null, company);
    }

    /**
     * The previous company's sectors and country give way to the next one's; the admin's own text
     * stays — except a chip typed in exactly the previous company's spelling, which is indistinguishable.
     */
    public HiringPersona refiledFrom(PersonaSeed previous, PersonaSeed next) {
        if (previous != null && next != null
                && Objects.equals(previous.apolloAccountId(), next.apolloAccountId())) {
            return this;
        }
        return new HiringPersona(summary,
                refiled(sectors, sectorsOf(previous), sectorsOf(next)),
                competitors,
                refiled(geographies, countryOf(previous), countryOf(next)),
                notes);
    }

    private static List<String> sectorsOf(PersonaSeed company) {
        ResolvedIndustry industry = company == null ? null : Industries.resolve(company.industry());
        if (industry == null) {
            return List.of();
        }
        return Stream.of(Industries.displayNameOf(industry.label()), industry.label(), industry.sectorGroup())
                .filter(Objects::nonNull)
                .toList();
    }

    private static List<String> countryOf(PersonaSeed company) {
        return company == null || company.country() == null ? List.of() : List.of(company.country());
    }

    private static List<String> refiled(List<String> current, List<String> filledBefore, List<String> filledNow) {
        Set<String> dropped = filledBefore.stream()
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<String> kept = current.stream()
                .filter(value -> !dropped.contains(value.toLowerCase(Locale.ROOT)))
                .toList();
        return distinct(Stream.concat(filledNow.stream(), kept.stream()).toList()).stream()
                .limit(MAX_LIST_ITEMS)
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Trimmed, blanks dropped, and case-insensitive repeats collapsed onto the first spelling. */
    private static List<String> distinct(List<String> values) {
        if (values == null) {
            return List.of();
        }
        Map<String, String> firstSpelling = new LinkedHashMap<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                String trimmed = value.trim();
                firstSpelling.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
            }
        }
        return List.copyOf(new ArrayList<>(firstSpelling.values()));
    }
}
