package app.lightmove.api.report.service;

import app.lightmove.api.candidate.constant.CandidateStatus;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.common.location.service.Countries;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.ReportSettings;
import app.lightmove.api.report.dto.BreakdownDto;
import app.lightmove.api.report.dto.LevelCountDto;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Chapter two. Sector comes from the universe company an executive is mapped at, seniority from the
 * executive; the matrix crosses the two. A hub is a city: executives group by their own city, the
 * same reading the map and the grid's Location column give, and one with no city on file is
 * unlocated whatever country it names.
 */
@Component
class MarketShapeReporter {

    static final String OTHER = "Other";
    private static final int EMPLOYERS_PER_HUB = 3;

    private final ReportSettings caps;

    MarketShapeReporter(LightMoveProperties properties) {
        this.caps = properties.report();
    }

    MarketShapeDto report(ReportSources sources) {
        List<ExecutiveRow> executives = sources.executives();
        List<String> sectors = leadingSectors(executives);
        List<PlacedExecutive> placed = executives.stream()
                .filter(row -> row.sector().isPresent() && row.seniority() != null)
                .map(row -> new PlacedExecutive(row, sectorLabel(row, sectors), row.seniority()))
                .toList();

        List<MarketCellDto> cells = new ArrayList<>();
        List<MarketSliceDto> slices = new ArrayList<>();
        for (String sector : sectors) {
            for (Seniority level : Seniority.values()) {
                List<ExecutiveRow> inCell = placed.stream()
                        .filter(candidate -> candidate.sector().equals(sector) && candidate.level() == level)
                        .map(PlacedExecutive::row)
                        .toList();
                cells.add(new MarketCellDto(sector, level.value(), inCell.size()));
                if (!inCell.isEmpty()) {
                    slices.add(slice(sector, level, inCell));
                }
            }
        }

        Hubs hubs = hubs(executives);
        return new MarketShapeDto(sectors, levelTokens(), cells,
                (int) executives.stream().filter(row -> row.sector().isEmpty()).count(),
                (int) executives.stream().filter(row -> row.seniority() == null).count(),
                slices, hubs.leading(), hubs.elsewhere(), hubs.unlocated(), companiesBySector(sources.universe()));
    }

    /** The most-populated sectors, and "Other" for the tail once the cap is passed. */
    private List<String> leadingSectors(List<ExecutiveRow> executives) {
        Tally<String> bySector = new Tally<>();
        executives.forEach(row -> row.sector().ifPresent(bySector::add));
        List<String> leading = new ArrayList<>(bySector.top(caps.maxSectors()));
        if (bySector.outside(leading) > 0) {
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

    private Hubs hubs(List<ExecutiveRow> executives) {
        Map<HubKey, List<ExecutiveRow>> byHub = new LinkedHashMap<>();
        int unlocated = 0;
        for (ExecutiveRow row : executives) {
            Optional<HubKey> hub = HubKey.of(row);
            if (hub.isEmpty()) {
                unlocated++;
                continue;
            }
            byHub.computeIfAbsent(hub.get(), ignored -> new ArrayList<>()).add(row);
        }
        List<Map.Entry<HubKey, List<ExecutiveRow>>> ranked = byHub.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<HubKey, List<ExecutiveRow>> entry) -> entry.getValue().size())
                        .reversed())
                .toList();
        List<TalentHubDto> leading = ranked.stream()
                .limit(caps.maxHubs())
                .map(entry -> hub(entry.getKey(), entry.getValue()))
                .toList();
        int elsewhere = ranked.stream().skip(caps.maxHubs()).mapToInt(entry -> entry.getValue().size()).sum();
        return new Hubs(leading, elsewhere, unlocated);
    }

    private static TalentHubDto hub(HubKey key, List<ExecutiveRow> here) {
        List<LevelCountDto> depth = Arrays.stream(Seniority.values())
                .map(level -> new LevelCountDto(level.value(),
                        (int) here.stream().filter(row -> row.seniority() == level).count()))
                .filter(count -> count.count() > 0)
                .toList();
        int interested = (int) here.stream().filter(row -> row.status() == CandidateStatus.INTERESTED).count();
        return new TalentHubDto(key.city(), key.country(), here.size(), depth,
                employersOf(here, EMPLOYERS_PER_HUB), interested);
    }

    private static List<String> employersOf(List<ExecutiveRow> rows, int limit) {
        Tally<String> byEmployer = new Tally<>();
        rows.stream().map(ExecutiveRow::employerName).filter(name -> name != null).forEach(byEmployer::add);
        return byEmployer.top(limit);
    }

    private List<BreakdownDto> companiesBySector(List<TriageCompanyResponse> universe) {
        Tally<String> bySector = new Tally<>();
        universe.forEach(company -> bySector.add(company.industry() == null ? OTHER : company.industry()));
        List<String> leading = bySector.top(caps.maxSectors());
        List<BreakdownDto> breakdown = leading.stream()
                .map(sector -> new BreakdownDto(sector, bySector.of(sector)))
                .collect(Collectors.toCollection(ArrayList::new));
        int tail = bySector.outside(leading);
        if (tail > 0) {
            breakdown.add(new BreakdownDto(OTHER, tail));
        }
        return breakdown;
    }

    static List<String> levelTokens() {
        return Arrays.stream(Seniority.values()).map(Seniority::value).toList();
    }

    private record PlacedExecutive(ExecutiveRow row, String sector, Seniority level) {}

    private record Hubs(List<TalentHubDto> leading, int elsewhere, int unlocated) {}

    /**
     * A city as the report groups by it, in the catalog's spelling, with its country so that two
     * cities of one name stay two hubs.
     */
    private record HubKey(String city, String country) {

        static Optional<HubKey> of(ExecutiveRow row) {
            String city = Countries.cityOf(row.executive().locationCity());
            if (isBlank(city)) {
                return Optional.empty();
            }
            String country = Countries.nameOf(row.executive().locationCountry());
            return Optional.of(new HubKey(city, isBlank(country) ? null : country));
        }

        private static boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }
}
