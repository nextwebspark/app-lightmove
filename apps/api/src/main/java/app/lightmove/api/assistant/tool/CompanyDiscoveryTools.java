package app.lightmove.api.assistant.tool;

import app.lightmove.api.core.config.AssistantSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.service.StrategyService;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import app.lightmove.api.triagecompany.model.MandateStages;
import app.lightmove.api.triagecompany.service.TriageCompanyReadService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * The model's side of finding companies by who they are like or what they do — {@link CompanyDiscovery}
 * does the finding; this records each step and keeps what was found for the suggested companies.
 */
@Component
public class CompanyDiscoveryTools {

    private static final int DEFAULT_COUNT = 10;
    private static final int MAX_ABOUT = 200;

    private final CompanyDiscovery discovery;
    private final StrategyService strategies;
    private final TriageCompanyReadService triaged;
    private final AssistantSettings settings;

    public CompanyDiscoveryTools(CompanyDiscovery discovery, StrategyService strategies,
                                 TriageCompanyReadService triaged, LightMoveProperties properties) {
        this.discovery = discovery;
        this.strategies = strategies;
        this.triaged = triaged;
        this.settings = properties.assistant();
    }

    @Tool(description = """
            Find which company a name means, before looking for companies like it. Answers every company \
            the name could be — in the company database first, on LinkedIn when the database has none — \
            each with what it does (about, niche keywords), its industry, city and headcount. matches is \
            ONE, SEVERAL (namesakes or sister companies — the consultant must choose) or NONE. Pass the \
            chosen one's apolloAccountId, or its linkedinSlug where it has no account id, to \
            findSimilarCompanies.""")
    public CompanyCandidates identifyCompany(
            @ToolParam(description = "The company's name as the consultant wrote it") String name,
            @ToolParam(required = false, description = "The country it is in, when the consultant said or the brief does")
            String country,
            ToolContext toolContext) {
        AssistantToolContext context = AssistantToolContext.from(toolContext);
        TurnRecorder recorder = context.recorder();
        String asked = name == null ? "" : name.strip();
        int step = recorder.startStep("Identifying " + (asked.isEmpty() ? "the company" : asked));
        if (asked.isEmpty()) {
            recorder.finishStep(step, "No name given");
            return CompanyCandidates.of(List.of());
        }
        CompanyDiscovery.Identified identified = discovery.identify(asked, country, settings.maxVendorSearchesPerAsk());
        recorder.countVendorSearches(identified.vendorSearches());
        List<CompanyProfile> profiles = identified.candidates().stream().map(this::profileOf).toList();
        CompanyCandidates candidates = CompanyCandidates.of(withStages(context, profiles));
        recorder.finishStep(step, switch (candidates.matches()) {
            case NONE -> "Not found";
            case ONE -> profiles.getFirst().companyName();
            case SEVERAL -> profiles.size() + " companies go by that name";
        });
        return candidates;
    }

    @Tool(description = """
            Find companies like one company — its competitors and peers: the same niche first (the rare \
            keywords they share), then the same sector, then a similar headcount. When too few match, the \
            headcount and then the sector are loosened, one at a time, and loosened lists each criterion \
            given up; the country never is. The company database is searched first and LinkedIn only for \
            what it lacks. Each company comes with an apolloAccountId or a linkedinSlug for \
            proposeCompanies, the niche keywords it shares, and a mandateStage when this mandate already \
            filed it. shortOf is how many fewer than asked could be found in those countries.""")
    public SimilarCompanies findSimilarCompanies(
            @ToolParam(description = "The apolloAccountId, or else the linkedinSlug, identifyCompany returned")
            String companyId,
            @ToolParam(required = false, description = "Countries to look in; the company's own country when omitted")
            List<String> countries,
            @ToolParam(required = false, description = "How many companies to find; 10 when omitted, at most 25")
            Integer count,
            ToolContext toolContext) {
        AssistantToolContext context = AssistantToolContext.from(toolContext);
        TurnRecorder recorder = context.recorder();
        CompanyDiscovery.Found anchor = discovery.anchorOf(companyId).orElse(null);
        if (anchor == null) {
            return SimilarCompanies.refused("No company has that id. Call identifyCompany and pass the "
                    + "apolloAccountId or linkedinSlug it returns.");
        }
        List<String> asked = MarketQuery.countriesOf(countries);
        List<String> searched = asked.isEmpty() && anchor.country() != null ? List.of(anchor.country()) : asked;
        int target = targetOf(count);
        int step = recorder.startStep("Finding companies like " + anchor.name()
                + (searched.isEmpty() ? "" : " in " + String.join(" or ", searched)));

        CompanyDiscovery.Discovered discovered = discovery.similarTo(anchor, searched, target, offLimits(context),
                settings.maxVendorSearchesPerAsk());
        List<DiscoveredCompany> companies = keep(context, discovered);
        recorder.countVendorSearches(discovered.vendorSearches());
        int shortOf = Math.max(0, target - companies.size());
        recorder.finishStep(step, describe(companies, discovered.loosened(), shortOf));
        CompanyProfile profile = withStages(context, List.of(profileOf(anchor))).getFirst();
        return new SimilarCompanies(profile, companies, discovered.loosened(), discovered.searchedLinkedIn(),
                shortOf, null);
    }

    @Tool(description = """
            Find companies by what they do when nobody has named one — "watch distributors", "luxury \
            boutiques", "cold-chain logistics". Pass one to four single words for the activity or the \
            product ("watch", "distributor"), not the country, the industry or the size, which have \
            their own arguments — and the country always, the brief's when the question gives none. The company database is searched by the keywords companies describe themselves \
            with, and LinkedIn for what it lacks. Each company comes with an apolloAccountId or a \
            linkedinSlug for proposeCompanies, the keywords that matched, and a mandateStage when this \
            mandate already filed it.""")
    public CompaniesByActivity searchCompaniesByActivity(
            @ToolParam(description = "One to four single words for what the companies do") List<String> words,
            @ToolParam(required = false, description = "Countries; at most five") List<String> countries,
            @ToolParam(required = false, description = "Industries, only when the consultant named a sector; at most five")
            List<String> industries,
            @ToolParam(required = false, description = "Fewest employees") Long minEmployees,
            @ToolParam(required = false, description = "Most employees") Long maxEmployees,
            @ToolParam(required = false, description = "How many companies to find; 10 when omitted, at most 25")
            Integer count,
            ToolContext toolContext) {
        AssistantToolContext context = AssistantToolContext.from(toolContext);
        TurnRecorder recorder = context.recorder();
        List<String> asked = MarketQuery.cleaned(words);
        List<String> searched = MarketQuery.countriesOf(countries);
        List<String> sectors = MarketQuery.industriesOf(industries);
        int target = targetOf(count);
        int step = recorder.startStep("Searching for " + String.join(" ", asked) + " companies"
                + (searched.isEmpty() ? "" : " in " + String.join(" or ", searched)));

        CompanyDiscovery.Discovered discovered = discovery.byActivity(asked, searched, sectors, minEmployees,
                maxEmployees, target, offLimits(context), settings.maxVendorSearchesPerAsk());
        List<DiscoveredCompany> companies = keep(context, discovered);
        recorder.countVendorSearches(discovered.vendorSearches());
        int shortOf = Math.max(0, target - companies.size());
        recorder.finishStep(step, describe(companies, List.of(), shortOf));
        return new CompaniesByActivity(asked, companies, discovered.searchedLinkedIn(), shortOf);
    }

    /** Recorded so {@code proposeCompanies} accepts them: a database row by its account id, a LinkedIn page by its slug. */
    private List<DiscoveredCompany> keep(AssistantToolContext context, CompanyDiscovery.Discovered discovered) {
        TurnRecorder recorder = context.recorder();
        List<DiscoveredCompany> companies = new ArrayList<>();
        for (CompanyDiscovery.Found one : discovered.companies()) {
            if (one.row() != null) {
                recorder.found(List.of(one.row().apolloAccountId()));
                companies.add(fromRow(one.row(), one.sharedNiche()));
                continue;
            }
            CapturedCompanyDetails details = one.details();
            if (details == null) {
                continue;
            }
            recorder.researched(one.page().linkedinSlug(), details);
            companies.add(new DiscoveredCompany(null, one.page().linkedinSlug(), details.companyName(),
                    details.industry(), details.companyCountry(), details.companyCity(), details.numEmployees(),
                    one.sharedNiche(), null));
        }
        MandateStages stages = triaged.stagesOf(context.workspaceId(), context.projectId(),
                companies.stream().map(DiscoveredCompany::apolloAccountId).filter(Objects::nonNull).toList(),
                companies.stream().map(DiscoveredCompany::companyName).toList());
        return companies.stream()
                .map(company -> company.inMandateAs(
                        stages.stageTokenOf(company.apolloAccountId(), company.companyName())))
                .toList();
    }

    private List<CompanyProfile> withStages(AssistantToolContext context, List<CompanyProfile> profiles) {
        MandateStages stages = triaged.stagesOf(context.workspaceId(), context.projectId(),
                profiles.stream().map(CompanyProfile::apolloAccountId).filter(Objects::nonNull).toList(),
                profiles.stream().map(CompanyProfile::companyName).toList());
        return profiles.stream()
                .map(profile -> profile.inMandateAs(stages.stageTokenOf(profile.apolloAccountId(), profile.companyName())))
                .toList();
    }

    private CompanyProfile profileOf(CompanyDiscovery.Found company) {
        List<String> niche = discovery.nicheOf(company);
        if (company.row() != null) {
            CompanyRow row = company.row();
            return new CompanyProfile(row.apolloAccountId(), company.slug(), row.companyName(), row.industry(),
                    row.companyCountry(), row.companyCity(), row.numEmployees(), shortened(row.shortDescription()),
                    niche, null);
        }
        CapturedCompanyDetails details = company.details();
        return new CompanyProfile(null, company.page().linkedinSlug(), company.name(),
                details == null ? null : details.industry(), company.country(),
                details == null ? null : details.companyCity(), company.employees(),
                shortened(company.page().about()), niche, null);
    }

    private Set<String> offLimits(AssistantToolContext context) {
        return Set.copyOf(strategies.scopeOf(context.workspaceId(), context.projectId()).offLimitsAccountIds());
    }

    private int targetOf(Integer count) {
        int asked = count == null || count < 1 ? DEFAULT_COUNT : count;
        return Math.min(asked, settings.toolRowLimit());
    }

    private static DiscoveredCompany fromRow(CompanyRow row, List<String> sharedNiche) {
        return new DiscoveredCompany(row.apolloAccountId(), null, row.companyName(), row.industry(),
                row.companyCountry(), row.companyCity(), row.numEmployees(),
                sharedNiche.stream().limit(5).toList(), null);
    }

    /** "8 found · 2 from LinkedIn · widened the size … · 2 short". */
    static String describe(List<DiscoveredCompany> companies, List<String> loosened, int shortOf) {
        if (companies.isEmpty()) {
            return "None found";
        }
        List<String> parts = new ArrayList<>();
        parts.add(companies.size() + " found");
        long fromLinkedIn = companies.stream().filter(company -> company.apolloAccountId() == null).count();
        if (fromLinkedIn > 0) {
            parts.add(fromLinkedIn + " from LinkedIn");
        }
        loosened.forEach(parts::add);
        long held = companies.stream().filter(company -> company.mandateStage() != null).count();
        if (held > 0) {
            parts.add(held + " already in the mandate");
        }
        if (shortOf > 0) {
            parts.add(shortOf + " short");
        }
        return String.join(" · ", parts);
    }

    private static String shortened(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String flattened = text.replaceAll("\\s+", " ").strip();
        return flattened.length() <= MAX_ABOUT ? flattened : flattened.substring(0, MAX_ABOUT) + "…";
    }
}
