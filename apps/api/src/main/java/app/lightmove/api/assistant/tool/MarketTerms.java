package app.lightmove.api.assistant.tool;

import app.lightmove.api.common.industry.service.Industries;
import app.lightmove.api.common.location.model.Country;
import app.lightmove.api.common.location.service.Countries;
import app.lightmove.api.strategy.service.SectorTaxonomy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reads a country and an industry the way a model writes them — "UAE", "the GCC", "banking",
 * "Technology" — into the exact values the universe is filtered on, so a search does not first need
 * {@code describeMarket} to learn the spelling. What it cannot read is reported, never guessed.
 *
 * <p>An industry label wins over a sector of the same name ("Financial Services"), as it does in
 * {@link Industries}: the narrower reading is the one the Strategy panel would also apply.
 */
@Component
@RequiredArgsConstructor
class MarketTerms {

    private static final List<String> GCC = List.of("AE", "SA", "QA", "KW", "BH", "OM");
    private static final List<String> MIDDLE_EAST = List.of(
            "AE", "SA", "QA", "KW", "BH", "OM", "JO", "LB", "IQ", "EG");

    private static final Map<String, List<String>> REGIONS = Map.of(
            "gcc", GCC,
            "gcc countries", GCC,
            "gulf", GCC,
            "arabian gulf", GCC,
            "gulf states", GCC,
            "gulf countries", GCC,
            "middle east", MIDDLE_EAST);

    private static final List<String> SECTOR_SUFFIXES = List.of(" sector", " industry", " industries");

    private final SectorTaxonomy taxonomy;

    ResolvedMarketTerms resolve(String country, String industry) {
        List<String> interpretedAs = new ArrayList<>();
        List<String> unrecognised = new ArrayList<>();
        List<String> countries = countriesOf(country, interpretedAs, unrecognised);
        List<String> industries = industriesOf(industry, interpretedAs, unrecognised);
        return new ResolvedMarketTerms(countries, labelOf(country, countries),
                industries, labelOf(industry, industries), interpretedAs, unrecognised);
    }

    private static List<String> countriesOf(String spelling, List<String> interpretedAs,
                                            List<String> unrecognised) {
        if (isBlank(spelling)) {
            return List.of();
        }
        String key = regionKeyOf(spelling);
        List<String> region = REGIONS.get(key);
        if (region != null) {
            List<String> names = region.stream().map(code -> Countries.nameOfCode(code).orElseThrow()).toList();
            interpretedAs.add(spelling.strip() + " → " + String.join(", ", names));
            return names;
        }
        Optional<Country> country = Countries.resolve(spelling).or(() -> Countries.resolve(key));
        if (country.isEmpty()) {
            unrecognised.add("country \"" + spelling.strip() + "\"");
            return List.of();
        }
        return List.of(country.get().name());
    }

    private List<String> industriesOf(String spelling, List<String> interpretedAs, List<String> unrecognised) {
        if (isBlank(spelling)) {
            return List.of();
        }
        for (String candidate : spellingsOf(spelling)) {
            if (Industries.isKnown(candidate)) {
                return List.of(Industries.nameOf(candidate));
            }
            Optional<Map.Entry<String, List<String>>> sector = sectorNamed(candidate);
            if (sector.isPresent()) {
                interpretedAs.add(sector.get().getKey() + " → " + String.join(", ", sector.get().getValue()));
                return sector.get().getValue();
            }
        }
        unrecognised.add("industry \"" + spelling.strip() + "\"");
        return List.of();
    }

    private Optional<Map.Entry<String, List<String>>> sectorNamed(String spelling) {
        String folded = folded(spelling);
        return taxonomy.groups().entrySet().stream()
                .filter(group -> folded(group.getKey()).equals(folded))
                .findFirst();
    }

    /** The spelling as given, then without a trailing "sector" or "industry". */
    private static List<String> spellingsOf(String spelling) {
        String stripped = spelling.strip();
        String lower = stripped.toLowerCase(Locale.ROOT);
        for (String suffix : SECTOR_SUFFIXES) {
            if (lower.endsWith(suffix) && lower.length() > suffix.length()) {
                return List.of(stripped, stripped.substring(0, stripped.length() - suffix.length()));
            }
        }
        return List.of(stripped);
    }

    /** "The U.A.E." and "Saudi-Arabia" read as the catalog's spellings do. */
    private static String regionKeyOf(String spelling) {
        String key = spelling.toLowerCase(Locale.ROOT)
                .replace('-', ' ')
                .replace(".", "")
                .replaceAll("\\s+", " ")
                .strip();
        return key.startsWith("the ") ? key.substring(4) : key;
    }

    /** Case, "and" against "&" and punctuation apart — "retail and consumer" is Retail & Consumer. */
    private static String folded(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace(" and ", " & ")
                .replaceAll("[^a-z0-9&]", "");
    }

    /** What the step line calls the axis: the resolved spelling when there is one, else the caller's. */
    private static String labelOf(String asked, List<String> resolved) {
        if (isBlank(asked)) {
            return null;
        }
        return resolved.size() == 1 ? resolved.getFirst() : asked.strip();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
