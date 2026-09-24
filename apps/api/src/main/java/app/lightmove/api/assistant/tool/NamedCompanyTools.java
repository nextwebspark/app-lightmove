package app.lightmove.api.assistant.tool;

import static app.lightmove.api.assistant.tool.NamedCompanyFinding.Status.NOT_CHECKED;
import static app.lightmove.api.assistant.tool.NamedCompanyFinding.Status.OFF_LIMITS;
import static app.lightmove.api.assistant.tool.NamedCompanyFinding.Status.RESEARCHED;
import static app.lightmove.api.assistant.tool.NamedCompanyFinding.Status.UNIVERSE;
import static app.lightmove.api.assistant.tool.NamedCompanyFinding.Status.UNVERIFIED;

import app.lightmove.api.common.location.service.Countries;
import app.lightmove.api.core.text.service.LinkedInUrls;
import app.lightmove.api.enrichment.company.service.CompanyResearch;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import app.lightmove.api.strategy.service.StrategyService;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Checks the companies the model knows of by name — local ones and the global companies operating
 * in the country — since the universe misses some a consultant would expect. The model supplies names only: each is found in the universe or on its LinkedIn page,
 * and what is found is what the answer and the card carry.
 */
@Slf4j
@Component
public class NamedCompanyTools {

    static final int MAX_NAMES = 10;
    private static final int FEWEST_EMPLOYEES_ANYWHERE = 1_000;
    private static final int MAX_ABOUT = 200;

    private final ApolloCompanyQueryService market;
    private final StrategyService strategies;
    private final CompanyResearch research;
    private final Duration deadline;

    @Autowired
    public NamedCompanyTools(ApolloCompanyQueryService market, StrategyService strategies,
                             CompanyResearch research) {
        this(market, strategies, research, Duration.ofSeconds(25));
    }

    NamedCompanyTools(ApolloCompanyQueryService market, StrategyService strategies,
                      CompanyResearch research, Duration deadline) {
        this.market = market;
        this.strategies = strategies;
        this.research = research;
        this.deadline = deadline;
    }

    @Tool(description = """
            Check companies you know of by name, when the question asks for the leading or best \
            companies in a sector and country. Pass up to ten real, currently operating companies you \
            are confident of, by their common name — local ones and the global companies operating in \
            that country. For a global brand run there by a local franchise partner or distributor, \
            give the partner as localOperator (Carrefour in the UAE: Majid Al Futtaim). Each comes back \
            as UNIVERSE (in the company universe, with an Apollo account id), RESEARCHED (found on \
            LinkedIn, with a LinkedIn slug and its real headcount), OFF_LIMITS (ruled out by the \
            client), UNVERIFIED (could not be confirmed anywhere) or NOT_CHECKED. global marks a \
            company found as its own headquarters abroad; operates names the brand a company runs \
            locally. State figures only as returned here. Call it once per answer.""")
    public NamedCompanies lookUpCompaniesByName(
            @ToolParam(description = "Companies, at most ten") List<NamedCompanyRequest> companies,
            @ToolParam(description = "Country the companies operate in") String country,
            ToolContext toolContext) {
        AssistantToolContext context = AssistantToolContext.from(toolContext);
        TurnRecorder recorder = context.recorder();
        if (!recorder.startNameLookup()) {
            return new NamedCompanies(List.of(), "Names were already looked up in this answer; use those results.");
        }
        List<NamedCompanyRequest> asked = requested(companies);
        int step = recorder.startStep("Checking " + asked.size() + " "
                + (asked.size() == 1 ? "company" : "companies") + " from knowledge");

        Set<String> offLimits = Set.copyOf(
                strategies.scopeOf(context.workspaceId(), context.projectId()).offLimitsAccountIds());
        List<NamedCompanyFinding> findings = new ArrayList<>();
        for (Resolved one : lookUpAll(asked, country)) {
            NamedCompanyFinding finding = one.finding(offLimits);
            findings.add(finding);
            record(recorder, finding, one.details());
        }

        recorder.finishStep(step, describe(findings));
        return new NamedCompanies(findings, null);
    }

    private static void record(TurnRecorder recorder, NamedCompanyFinding finding, CapturedCompanyDetails details) {
        String key = switch (finding.status()) {
            case UNIVERSE -> {
                recorder.found(List.of(finding.apolloAccountId()));
                yield finding.apolloAccountId();
            }
            case RESEARCHED -> {
                recorder.researched(finding.linkedinSlug(), details);
                yield finding.linkedinSlug();
            }
            default -> null;
        };
        if (key != null && finding.operates() != null) {
            recorder.operates(key, finding.operates());
        }
    }

    private static List<NamedCompanyRequest> requested(List<NamedCompanyRequest> companies) {
        if (companies == null) {
            return List.of();
        }
        Set<String> seen = new HashSet<>();
        return companies.stream()
                .filter(company -> company != null && company.name() != null && !company.name().isBlank())
                .map(company -> new NamedCompanyRequest(company.name().strip(),
                        company.localOperator() == null || company.localOperator().isBlank()
                                ? null : company.localOperator().strip()))
                .filter(company -> seen.add(company.name().toLowerCase(Locale.ROOT)))
                .limit(MAX_NAMES)
                .toList();
    }

    /**
     * Every name at once, on virtual threads, inside one deadline — the answer streams under a
     * minute. A name still waiting on the provider is reported as not checked and not waited for.
     */
    private List<Resolved> lookUpAll(List<NamedCompanyRequest> companies, String country) {
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<Callable<Resolved>> lookups = companies.stream()
                    .map(company -> (Callable<Resolved>) () -> lookUp(company, country))
                    .toList();
            List<Future<Resolved>> answers = pool.invokeAll(lookups, deadline.toMillis(), TimeUnit.MILLISECONDS);
            List<Resolved> resolved = new ArrayList<>();
            for (int index = 0; index < companies.size(); index++) {
                resolved.add(outcomeOf(companies.get(index).name(), answers.get(index)));
            }
            return resolved;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return companies.stream().map(company -> Resolved.unresolved(company.name(), NOT_CHECKED)).toList();
        } finally {
            pool.shutdownNow();
        }
    }

    private static Resolved outcomeOf(String name, Future<Resolved> answer) {
        try {
            return answer.get();
        } catch (CancellationException timedOut) {
            return Resolved.unresolved(name, NOT_CHECKED);
        } catch (ExecutionException failed) {
            log.warn("Looking up company {} failed", name, failed.getCause());
            return Resolved.unresolved(name, NOT_CHECKED);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Resolved.unresolved(name, NOT_CHECKED);
        }
    }

    /**
     * The local operator first, since that is where the executives are; then the brand in the
     * country; then the brand's own page wherever it is headquartered.
     */
    private Resolved lookUp(NamedCompanyRequest company, String country) {
        if (company.localOperator() != null) {
            Optional<Resolved> operator = inCountry(company.localOperator(), country);
            if (operator.isPresent()) {
                return operator.get().operating(company.name());
            }
        }
        return inCountry(company.name(), country)
                .or(() -> anywhere(company.name()))
                .orElseGet(() -> Resolved.unresolved(company.name(), UNVERIFIED));
    }

    /**
     * The universe is asked first because it costs nothing. A company found on LinkedIn is asked
     * about again by its slug, since the universe often holds it under a longer legal name.
     */
    private Optional<Resolved> inCountry(String name, String country) {
        String countryName = Countries.nameOf(country);
        Optional<CompanyRow> byName = market.matchEmployer(null, name)
                .filter(row -> countryName == null || countryName.equalsIgnoreCase(row.companyCountry()));
        if (byName.isPresent()) {
            return Optional.of(Resolved.inUniverse(name, byName.get(), false));
        }
        return research.byName(name, country).flatMap(details -> resolvedPage(name, details, false));
    }

    /** A global company is a big one: a small namesake abroad is someone else. */
    private Optional<Resolved> anywhere(String name) {
        Optional<CompanyRow> byName = market.matchEmployer(null, name)
                .filter(row -> row.numEmployees() != null && row.numEmployees() >= FEWEST_EMPLOYEES_ANYWHERE);
        if (byName.isPresent()) {
            return Optional.of(Resolved.inUniverse(name, byName.get(), true));
        }
        return research.byNameAnywhere(name).flatMap(details -> resolvedPage(name, details, true));
    }

    private Optional<Resolved> resolvedPage(String name, CapturedCompanyDetails details, boolean global) {
        String slug = LinkedInUrls.companySlugOrNull(details.companyLinkedinUrl());
        if (slug == null) {
            return Optional.empty();
        }
        return Optional.of(market.matchEmployer(slug, details.companyName())
                .map(row -> Resolved.inUniverse(name, row, global))
                .orElseGet(() -> new Resolved(name, null, slug, details, null, null, global)));
    }

    /** "3 in the universe · 2 researched · 2 global · 1 couldn't be verified". */
    static String describe(List<NamedCompanyFinding> findings) {
        Map<NamedCompanyFinding.Status, Long> counts = findings.stream()
                .collect(Collectors.groupingBy(NamedCompanyFinding::status, Collectors.counting()));
        Map<NamedCompanyFinding.Status, Function<Long, String>> phrases = new LinkedHashMap<>();
        phrases.put(UNIVERSE, count -> count + " in the universe");
        phrases.put(RESEARCHED, count -> count + " researched");
        phrases.put(OFF_LIMITS, count -> count + " off limits");
        phrases.put(UNVERIFIED, count -> count + " couldn't be verified");
        phrases.put(NOT_CHECKED, count -> count + " not checked in time");
        List<String> parts = new ArrayList<>(phrases.entrySet().stream()
                .filter(phrase -> counts.containsKey(phrase.getKey()))
                .map(phrase -> phrase.getValue().apply(counts.get(phrase.getKey())))
                .toList());
        long global = findings.stream().filter(NamedCompanyFinding::global).count();
        if (global > 0) {
            parts.add(Math.min(parts.size(), 2), global + " global");
        }
        return parts.isEmpty() ? "Nothing to check" : String.join(" · ", parts);
    }

    private record Resolved(String name, CompanyRow row, String slug, CapturedCompanyDetails details,
                            NamedCompanyFinding.Status failure, String operates, boolean global) {

        static Resolved unresolved(String name, NamedCompanyFinding.Status status) {
            return new Resolved(name, null, null, null, status, null, false);
        }

        static Resolved inUniverse(String name, CompanyRow row, boolean global) {
            return new Resolved(name, row, null, null, null, null, global);
        }

        Resolved operating(String brand) {
            return new Resolved(brand, row, slug, details, failure, brand, global);
        }

        NamedCompanyFinding finding(Set<String> offLimits) {
            if (row != null) {
                return offLimits.contains(row.apolloAccountId())
                        ? NamedCompanyFinding.unresolved(name, OFF_LIMITS)
                        : new NamedCompanyFinding(name, UNIVERSE, row.apolloAccountId(), null,
                                row.companyName(), row.companyCountry(), row.industry(), row.companyCity(),
                                row.numEmployees(), row.foundedYear(), shortened(row.shortDescription()),
                                operates, global);
            }
            if (details != null) {
                return new NamedCompanyFinding(name, RESEARCHED, null, slug, details.companyName(),
                        details.companyCountry(), details.industry(), details.companyCity(),
                        details.numEmployees(), details.foundedYear(), shortened(details.shortDescription()),
                        operates, global);
            }
            return NamedCompanyFinding.unresolved(name, failure);
        }
    }

    private static String shortened(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String flattened = text.replaceAll("\\s+", " ").strip();
        return flattened.length() <= MAX_ABOUT ? flattened : flattened.substring(0, MAX_ABOUT) + "…";
    }
}
