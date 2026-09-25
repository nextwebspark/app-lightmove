package app.lightmove.api.assistant.tool;

import static app.lightmove.api.assistant.tool.NamedCompanyFinding.Status.NOT_CHECKED;
import static app.lightmove.api.assistant.tool.NamedCompanyFinding.Status.OFF_LIMITS;
import static app.lightmove.api.assistant.tool.NamedCompanyFinding.Status.RESEARCHED;
import static app.lightmove.api.assistant.tool.NamedCompanyFinding.Status.UNIVERSE;
import static app.lightmove.api.assistant.tool.NamedCompanyFinding.Status.UNVERIFIED;

import app.lightmove.api.common.location.service.Countries;
import app.lightmove.api.core.config.AssistantSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.text.service.LinkedInUrls;
import app.lightmove.api.enrichment.company.model.VendorSearchAllowance;
import app.lightmove.api.enrichment.company.service.CompanyResearch;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Works out what each name the model remembered is: its local operator, then the company in the
 * country, then a global brand's own page — the universe first because it is free, LinkedIn through
 * Bright Data after it. Names run a few at a time inside one deadline and one budget of billed
 * searches, because each lookup holds database connections and each search is paid for.
 */
@Slf4j
@Component
public class NamedCompanyResolver {

    private static final int MAX_ABOUT = 200;

    private final ApolloCompanyQueryService market;
    private final CompanyResearch research;
    private final AssistantSettings settings;

    public NamedCompanyResolver(ApolloCompanyQueryService market, CompanyResearch research,
                                LightMoveProperties properties) {
        this.market = market;
        this.research = research;
        this.settings = properties.assistant();
    }

    /** One resolved name, with the page's full facts where it was found on LinkedIn. */
    public record ResolvedName(NamedCompanyFinding finding, CapturedCompanyDetails details) {}

    public record Resolution(List<ResolvedName> names, int vendorSearches) {}

    public Resolution resolve(List<NamedCompanyRequest> companies, String country, Set<String> offLimits) {
        VendorSearchAllowance allowance = new VendorSearchAllowance(settings.maxVendorSearchesPerAsk());
        List<ResolvedName> names = lookUpAll(companies, country, allowance).stream()
                .map(one -> new ResolvedName(one.finding(offLimits), one.details()))
                .toList();
        return new Resolution(names, allowance.used());
    }

    private List<Lookup> lookUpAll(List<NamedCompanyRequest> companies, String country,
                                   VendorSearchAllowance allowance) {
        ExecutorService pool = Executors.newFixedThreadPool(settings.nameLookupParallelism(),
                Thread.ofVirtual().factory());
        try {
            List<Callable<Lookup>> lookups = companies.stream()
                    .map(company -> (Callable<Lookup>) () -> lookUp(company, country, allowance))
                    .toList();
            List<Future<Lookup>> answers = pool.invokeAll(lookups,
                    settings.nameLookupDeadline().toMillis(), TimeUnit.MILLISECONDS);
            List<Lookup> resolved = new ArrayList<>();
            for (int index = 0; index < companies.size(); index++) {
                resolved.add(outcomeOf(companies.get(index).name(), answers.get(index)));
            }
            return resolved;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return companies.stream().map(company -> Lookup.unresolved(company.name(), NOT_CHECKED)).toList();
        } finally {
            pool.shutdownNow();
        }
    }

    private static Lookup outcomeOf(String name, Future<Lookup> answer) {
        try {
            return answer.get();
        } catch (CancellationException timedOut) {
            return Lookup.unresolved(name, NOT_CHECKED);
        } catch (ExecutionException failed) {
            log.warn("Looking up company {} failed", name, failed.getCause());
            return Lookup.unresolved(name, NOT_CHECKED);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Lookup.unresolved(name, NOT_CHECKED);
        }
    }

    private Lookup lookUp(NamedCompanyRequest company, String country, VendorSearchAllowance allowance) {
        if (company.localOperator() != null) {
            Optional<Lookup> operator = inCountry(company.localOperator(), country, allowance);
            if (operator.isPresent()) {
                return operator.get().operating(company.name());
            }
        }
        return inCountry(company.name(), country, allowance)
                .or(() -> anywhere(company.name(), allowance))
                .orElseGet(() -> Lookup.unresolved(company.name(), UNVERIFIED));
    }

    private Optional<Lookup> inCountry(String name, String country, VendorSearchAllowance allowance) {
        Optional<CompanyRow> held = market.largestNamed(name, Countries.nameOf(country), 0);
        if (held.isPresent()) {
            return Optional.of(Lookup.inUniverse(name, held.get(), false));
        }
        return research.byName(name, country, allowance).flatMap(details -> page(name, details, false));
    }

    private Optional<Lookup> anywhere(String name, VendorSearchAllowance allowance) {
        Optional<CompanyRow> held = market.largestNamed(name, null, CompanyResearch.MIN_EMPLOYEES_ANYWHERE);
        if (held.isPresent()) {
            return Optional.of(Lookup.inUniverse(name, held.get(), true));
        }
        return research.byNameAnywhere(name, allowance).flatMap(details -> page(name, details, true));
    }

    /** A page the universe already holds — often under a longer legal name — is the universe row. */
    private Optional<Lookup> page(String name, CapturedCompanyDetails details, boolean global) {
        String slug = LinkedInUrls.companySlugOrNull(details.companyLinkedinUrl());
        if (slug == null) {
            return Optional.empty();
        }
        return Optional.of(market.matchEmployer(slug, details.companyName())
                .map(row -> Lookup.inUniverse(name, row, global))
                .orElseGet(() -> new Lookup(name, null, slug, details, null, null, global)));
    }

    private record Lookup(String name, CompanyRow row, String slug, CapturedCompanyDetails details,
                          NamedCompanyFinding.Status failure, String operates, boolean global) {

        static Lookup unresolved(String name, NamedCompanyFinding.Status status) {
            return new Lookup(name, null, null, null, status, null, false);
        }

        static Lookup inUniverse(String name, CompanyRow row, boolean global) {
            return new Lookup(name, row, null, null, null, null, global);
        }

        Lookup operating(String brand) {
            return new Lookup(brand, row, slug, details, failure, brand, global);
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
