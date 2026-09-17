package app.lightmove.api.report.service;

import app.lightmove.api.candidate.constant.CandidateStatus;
import app.lightmove.api.candidate.constant.Gender;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.common.location.service.Countries;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.ReportSettings;
import app.lightmove.api.geocoding.model.GeoPoint;
import app.lightmove.api.geocoding.model.PlaceKey;
import app.lightmove.api.geocoding.service.GeocodingService;
import app.lightmove.api.report.dto.BreakdownDto;
import app.lightmove.api.report.dto.LevelCountDto;
import app.lightmove.api.report.dto.MapPointDto;
import app.lightmove.api.report.dto.MarketCellDto;
import app.lightmove.api.report.dto.MarketShapeDto;
import app.lightmove.api.report.dto.MarketSliceDto;
import app.lightmove.api.report.dto.SliceExecutiveDto;
import app.lightmove.api.report.dto.TalentHubDto;
import app.lightmove.api.report.model.ExecutiveRow;
import app.lightmove.api.report.model.ReportSources;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Chapter two. Sector comes from the universe company an executive is mapped at, seniority from the
 * executive; the matrix crosses the two.
 *
 * <p><b>A hub is a country.</b> It was a city until it turned out most researched rows carry a
 * country and no city — grouping by city dropped those rows onto "unlocated" and reported a market
 * as empty when it was merely imprecise. Country is the axis nearly every row can actually answer.
 */
@Component
class MarketShapeReporter {

    private static final String OTHER = ReportVocabulary.OTHER;

    private final ReportSettings caps;
    private final GeocodingService geocoding;

    MarketShapeReporter(LightMoveProperties properties, GeocodingService geocoding) {
        this.caps = properties.report();
        this.geocoding = geocoding;
    }

    MarketShapeDto report(ReportSources sources) {
        List<ExecutiveRow> executives = sources.executives();
        List<String> sectors = leadingSectors(executives);
        List<PlacedExecutive> placed = executives.stream()
                .filter(row -> row.sector().isPresent() && row.seniority() != null)
                .map(row -> new PlacedExecutive(row, sectorLabel(row, sectors), row.seniority()))
                .toList();

        Map<String, Map<Seniority, List<ExecutiveRow>>> byCell = placed.stream()
                .collect(Collectors.groupingBy(PlacedExecutive::sector, Collectors.groupingBy(PlacedExecutive::level,
                        Collectors.mapping(PlacedExecutive::row, Collectors.toList()))));
        List<MarketCellDto> cells = new ArrayList<>();
        List<MarketSliceDto> slices = new ArrayList<>();
        for (String sector : sectors) {
            for (Seniority level : Seniority.values()) {
                List<ExecutiveRow> inCell = byCell.getOrDefault(sector, Map.of()).getOrDefault(level, List.of());
                cells.add(new MarketCellDto(sector, level.value(), inCell.size()));
                if (!inCell.isEmpty()) {
                    slices.add(slice(sector, level, inCell));
                }
            }
        }

        Hubs hubs = hubs(executives, sources.compensation().currency());
        return new MarketShapeDto(sectors, ReportVocabulary.levelTokens(), cells,
                (int) executives.stream().filter(row -> row.sector().isEmpty()).count(),
                (int) executives.stream().filter(row -> row.seniority() == null).count(),
                slices, hubs.leading(), hubs.elsewhere(), hubs.unlocated(), companiesBySector(sources.universe()));
    }

    /** The most-populated sectors, and "Other" for the tail once the cap is passed. */
    private List<String> leadingSectors(List<ExecutiveRow> executives) {
        Tally<String> bySector = new Tally<>();
        executives.forEach(row -> row.sector().ifPresent(bySector::add));
        List<String> leading = new ArrayList<>(bySector.top(caps.maxSectors()));
        if (bySector.outside(leading) > 0 && !leading.contains(OTHER)) {
            leading.add(OTHER);
        }
        return leading;
    }

    private static String sectorLabel(ExecutiveRow row, List<String> sectors) {
        String sector = row.sector().orElseThrow();
        return sectors.contains(sector) ? sector : OTHER;
    }

    private MarketSliceDto slice(String sector, Seniority level, List<ExecutiveRow> inCell) {
        List<SliceExecutiveDto> listed = inCell.stream()
                .limit(caps.maxExecutivesPerSlice())
                .map(row -> new SliceExecutiveDto(row.executive().id(), row.executive().fullName(),
                        row.employerName(), row.executive().status()))
                .toList();
        return new MarketSliceDto(sector, level.value(), employersOf(inCell, Integer.MAX_VALUE), listed);
    }

    private Hubs hubs(List<ExecutiveRow> executives, String currency) {
        Map<String, List<ExecutiveRow>> byCountry = new LinkedHashMap<>();
        Tally<String> headcount = new Tally<>();
        int unlocated = 0;
        for (ExecutiveRow row : executives) {
            String country = Countries.nameOf(row.executive().locationCountry());
            if (country == null || country.isBlank()) {
                unlocated++;
                continue;
            }
            byCountry.computeIfAbsent(country, ignored -> new ArrayList<>()).add(row);
            headcount.add(country);
        }
        List<String> leading = headcount.top(caps.maxHubs());
        Map<PlaceKey, GeoPoint> points = pointsFor(leading);
        List<TalentHubDto> hubs = leading.stream()
                .map(country -> hub(country, byCountry.get(country), currency, points))
                .toList();
        return new Hubs(hubs, headcount.outside(leading), unlocated);
    }

    /**
     * Points for the countries the chapter names, and only those: the cache answers most of them for
     * nothing, and a mandate whose places nobody has resolved yet draws the bars without the map
     * rather than spending this read's whole vendor budget on it.
     */
    private Map<PlaceKey, GeoPoint> pointsFor(List<String> countries) {
        Set<PlaceKey> places = countries.stream()
                .map(country -> PlaceKey.of(null, country))
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return places.isEmpty() ? Map.of() : geocoding.resolve(places).points();
    }

    private TalentHubDto hub(String country, List<ExecutiveRow> here, String currency,
                             Map<PlaceKey, GeoPoint> points) {
        List<LevelCountDto> depth = Arrays.stream(Seniority.values())
                .map(level -> new LevelCountDto(level.value(),
                        (int) here.stream().filter(row -> row.seniority() == level).count()))
                .filter(count -> count.count() > 0)
                .toList();
        int interested = (int) here.stream().filter(row -> row.status() == CandidateStatus.INTERESTED).count();
        int gccNationals = (int) here.stream()
                .filter(row -> NationalityCatalog.isGcc(NationalityCatalog.groupOf(row.executive().nationality())))
                .count();
        int female = (int) here.stream().filter(row -> row.gender() == Gender.FEMALE).count();
        int recordedGender = (int) here.stream().filter(row -> row.gender() != null).count();
        return new TalentHubDto(country, here.size(), depth,
                employersOf(here, caps.maxEmployersPerHub()), interested, gccNationals, female, recordedGender,
                medianPackageOf(here, currency), pointOf(country, points));
    }

    /** The middle disclosed package here, in the brief's currency. Another currency is left out, never converted. */
    private static Long medianPackageOf(List<ExecutiveRow> here, String currency) {
        return Packages.medianOf(here.stream()
                .map(row -> row.executive().compensation())
                .filter(Packages::isDisclosed)
                .filter(compensation -> Packages.isInCurrency(compensation, currency))
                .map(Packages::totalOf)
                .toList());
    }

    private static MapPointDto pointOf(String country, Map<PlaceKey, GeoPoint> points) {
        return PlaceKey.of(null, country)
                .map(points::get)
                .map(point -> new MapPointDto(point.latitude(), point.longitude()))
                .orElse(null);
    }

    private static List<String> employersOf(List<ExecutiveRow> rows, int limit) {
        Tally<String> byEmployer = new Tally<>();
        rows.stream().map(ExecutiveRow::employerName).filter(name -> name != null).forEach(byEmployer::add);
        return byEmployer.top(limit);
    }

    private List<BreakdownDto> companiesBySector(List<TriageCompanyResponse> universe) {
        Tally<String> bySector = new Tally<>();
        universe.forEach(company -> bySector.add(company.industry() == null ? OTHER : company.industry()));
        List<String> named = bySector.top(caps.maxSectors()).stream()
                .filter(sector -> !OTHER.equals(sector))
                .toList();
        List<BreakdownDto> breakdown = named.stream()
                .map(sector -> new BreakdownDto(sector, bySector.of(sector)))
                .collect(Collectors.toCollection(ArrayList::new));
        int other = bySector.outside(named);
        if (other > 0) {
            breakdown.add(new BreakdownDto(OTHER, other));
        }
        return breakdown;
    }

    private record PlacedExecutive(ExecutiveRow row, String sector, Seniority level) {}

    private record Hubs(List<TalentHubDto> leading, int elsewhere, int unlocated) {}

}
