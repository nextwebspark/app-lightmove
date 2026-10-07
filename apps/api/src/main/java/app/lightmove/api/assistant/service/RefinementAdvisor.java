package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.constant.RefinementKind;
import app.lightmove.api.assistant.model.AssistantRefinement;
import app.lightmove.api.assistant.model.AssistantRefinements;
import app.lightmove.api.assistant.tool.AssistantToolContext;
import app.lightmove.api.assistant.tool.MarketAsk;
import app.lightmove.api.common.location.service.Countries;
import app.lightmove.api.core.config.AssistantSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.strategy.constant.CompanySortField;
import app.lightmove.api.strategy.constant.SortDirection;
import app.lightmove.api.strategy.model.CompanyExclusion;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import app.lightmove.api.strategy.service.IndustryAdjacency;
import app.lightmove.api.strategy.service.StrategyService;
import app.lightmove.api.strategy.service.TriagedCompanyLookup;
import app.lightmove.api.triagecompany.dto.TriageCountsDto;
import app.lightmove.api.triagecompany.service.TriageCompanyReadService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Offers the next searches that would bring the mandate's universe — In universe and Shortlisted plus
 * the new companies this answer's search found — toward the target band. Every figure is a count of the
 * universe, never the model's estimate, and no model is called.
 */
@Service
public class RefinementAdvisor {

    static final int MAX_OPTIONS = 3;

    /** Count queries one answer may spend weighing levers. */
    static final int MAX_LEVERS_COUNTED = 8;

    private static final int ADJACENT_LEVERS = 3;
    private static final int COUNTRY_LEVERS = 3;
    private static final long[] READABLE_HEADCOUNTS =
            {50, 100, 250, 500, 1_000, 2_500, 5_000, 10_000, 25_000, 50_000, 100_000};

    private final ApolloCompanyQueryService market;
    private final StrategyService strategies;
    private final TriagedCompanyLookup filedCompanies;
    private final TriageCompanyReadService triaged;
    private final IndustryAdjacency adjacency;
    private final AssistantSettings settings;

    public RefinementAdvisor(ApolloCompanyQueryService market, StrategyService strategies,
                             TriagedCompanyLookup filedCompanies, TriageCompanyReadService triaged,
                             IndustryAdjacency adjacency, LightMoveProperties properties) {
        this.market = market;
        this.strategies = strategies;
        this.filedCompanies = filedCompanies;
        this.triaged = triaged;
        this.adjacency = adjacency;
        this.settings = properties.assistant();
    }

    /** Does nothing for an answer that ran no market search. */
    public void offer(AssistantToolContext context) {
        MarketAsk asked = context.recorder().lastMarketAsk();
        if (asked == null) {
            return;
        }
        TriageCountsDto stages = triaged.stageCountsOf(context.workspaceId(), context.projectId());
        long inMandate = stages.inUniverse() + stages.shortlisted();
        List<String> offLimits = strategies.scopeOf(context.workspaceId(), context.projectId()).offLimitsAccountIds();
        CompanyExclusion filed = filedCompanies.exclusionFor(context.projectId());
        ToLongFunction<MarketAsk> newCompanies = ask -> market.count(ask.newCompaniesScope(offLimits, filed));

        long fresh = newCompanies.applyAsLong(asked);
        long projected = inMandate + fresh;
        List<Lever> levers;
        if (projected < settings.targetUniverseMin()) {
            levers = widening(asked);
        } else if (projected > settings.targetUniverseMax() && inMandate < settings.targetUniverseMax()) {
            levers = narrowing(asked, inMandate, offLimits, filed);
        } else {
            levers = List.of();
        }
        List<AssistantRefinement> options = levers.stream()
                .limit(MAX_LEVERS_COUNTED)
                .map(lever -> new AssistantRefinement(lever.kind(), lever.label(), lever.ask().asQuestion(),
                        inMandate + newCompanies.applyAsLong(lever.ask())))
                .filter(option -> projected < settings.targetUniverseMin()
                        ? option.projected() > projected
                        : option.projected() < projected)
                .sorted(Comparator.comparingLong(this::distanceFromTarget)
                        .thenComparingLong(option -> Math.abs(option.projected() - midpoint())))
                .limit(MAX_OPTIONS)
                .toList();
        context.recorder().offerRefinements(new AssistantRefinements(inMandate, fresh, projected,
                settings.targetUniverseMin(), settings.targetUniverseMax(), options));
    }

    /** Most specific first: a keyword or a size floor the consultant set is the likeliest thing holding it back. */
    private List<Lever> widening(MarketAsk asked) {
        List<Lever> levers = new ArrayList<>();
        if (asked.keyword() != null) {
            levers.add(new Lever(RefinementKind.DROP_KEYWORD, "Drop \"" + asked.keyword() + "\"",
                    asked.withoutKeyword()));
        }
        if (asked.minEmployees() != null || asked.maxEmployees() != null) {
            levers.add(new Lever(RefinementKind.ANY_SIZE, "Any company size", asked.ofAnySize()));
        }
        if (!asked.industries().isEmpty() && asked.hasRoomFor(asked.industries())) {
            Set<String> held = lowerCased(asked.industries());
            asked.industries().stream()
                    .flatMap(industry -> adjacency.neighboursOf(industry).stream())
                    .filter(neighbour -> !held.contains(neighbour.toLowerCase(Locale.ROOT)))
                    .distinct()
                    .limit(ADJACENT_LEVERS)
                    .forEach(neighbour -> levers.add(new Lever(RefinementKind.ADD_INDUSTRY,
                            "Add " + capitalised(neighbour), asked.withIndustry(neighbour))));
        }
        neighbourCountriesOf(asked).stream()
                .limit(COUNTRY_LEVERS)
                .forEach(country -> levers.add(new Lever(RefinementKind.ADD_COUNTRY, "Also " + country,
                        asked.withCountry(country))));
        return levers;
    }

    private List<Lever> narrowing(MarketAsk asked, long inMandate, List<String> offLimits, CompanyExclusion filed) {
        List<Lever> levers = new ArrayList<>();
        long room = inMandate < midpoint() ? midpoint() - inMandate : settings.targetUniverseMax() - inMandate;
        headcountAtRank(asked, room, offLimits, filed).ifPresent(headcount -> {
            for (long floor : readableFloorsAround(headcount)) {
                if (asked.minEmployees() == null || floor > asked.minEmployees()) {
                    levers.add(new Lever(RefinementKind.SIZE_FLOOR,
                            String.format(Locale.ROOT, "%,d+ staff only", floor), asked.withMinEmployees(floor)));
                }
            }
        });
        if (asked.countries().size() > 1) {
            asked.countries().forEach(country -> levers.add(new Lever(RefinementKind.ONE_COUNTRY,
                    country + " only", asked.onlyIn(country))));
        }
        if (asked.industries().size() > 1) {
            asked.industries().forEach(industry -> levers.add(new Lever(RefinementKind.DROP_INDUSTRY,
                    "Drop " + capitalised(industry), asked.withoutIndustry(industry))));
        }
        return levers;
    }

    /** The headcount of the {@code rank}-th largest new company: a floor there leaves about that many. */
    private Optional<Long> headcountAtRank(MarketAsk asked, long rank, List<String> offLimits,
                                           CompanyExclusion filed) {
        if (rank < 1) {
            return Optional.empty();
        }
        List<CompanyRow> row = market.search(asked.newCompaniesScope(offLimits, filed), CompanySortField.EMPLOYEES,
                SortDirection.DESC, (int) rank - 1, 1);
        return row.stream().map(CompanyRow::numEmployees).filter(employees -> employees != null && employees > 0)
                .map(Integer::longValue).findFirst();
    }

    /** The readable headcounts either side of the exact one, so the consultant reads "1,000+" rather than "1,137+". */
    static List<Long> readableFloorsAround(long headcount) {
        List<Long> floors = new ArrayList<>(2);
        for (int index = READABLE_HEADCOUNTS.length - 1; index >= 0; index--) {
            if (READABLE_HEADCOUNTS[index] <= headcount) {
                floors.add(READABLE_HEADCOUNTS[index]);
                if (index + 1 < READABLE_HEADCOUNTS.length && READABLE_HEADCOUNTS[index] != headcount) {
                    floors.add(READABLE_HEADCOUNTS[index + 1]);
                }
                return floors;
            }
        }
        floors.add(READABLE_HEADCOUNTS[0]);
        return floors;
    }

    /** Offered only beside a search whose every country is a neighbour already, so a UK search never grows into the Gulf. */
    private List<String> neighbourCountriesOf(MarketAsk asked) {
        List<String> neighbours = settings.refinementNeighbourCountryCodes().stream()
                .map(Countries::nameOfCode)
                .flatMap(Optional::stream)
                .toList();
        if (asked.countries().isEmpty() || !asked.hasRoomFor(asked.countries())
                || !lowerCased(neighbours).containsAll(lowerCased(asked.countries()))) {
            return List.of();
        }
        Set<String> held = lowerCased(asked.countries());
        return neighbours.stream().filter(country -> !held.contains(country.toLowerCase(Locale.ROOT))).toList();
    }

    private long distanceFromTarget(AssistantRefinement option) {
        if (option.projected() < settings.targetUniverseMin()) {
            return settings.targetUniverseMin() - option.projected();
        }
        return Math.max(0, option.projected() - settings.targetUniverseMax());
    }

    private long midpoint() {
        return (settings.targetUniverseMin() + settings.targetUniverseMax()) / 2;
    }

    private static Set<String> lowerCased(List<String> values) {
        return values.stream().map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    private static String capitalised(String label) {
        return label.isEmpty() ? label : label.substring(0, 1).toUpperCase(Locale.ROOT) + label.substring(1);
    }

    private record Lever(RefinementKind kind, String label, MarketAsk ask) {}
}
