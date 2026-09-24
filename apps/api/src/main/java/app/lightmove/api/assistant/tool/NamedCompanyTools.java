package app.lightmove.api.assistant.tool;

import static app.lightmove.api.assistant.tool.NamedCompanyFinding.Status.NOT_CHECKED;
import static app.lightmove.api.assistant.tool.NamedCompanyFinding.Status.OFF_LIMITS;
import static app.lightmove.api.assistant.tool.NamedCompanyFinding.Status.RESEARCHED;
import static app.lightmove.api.assistant.tool.NamedCompanyFinding.Status.UNIVERSE;
import static app.lightmove.api.assistant.tool.NamedCompanyFinding.Status.UNVERIFIED;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.strategy.service.StrategyService;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * The model's side of checking companies it knows of by name — local ones and the global companies
 * operating in the country. {@link NamedCompanyResolver} does the checking; this records it as a step
 * and keeps what was found for the card.
 */
@Component
public class NamedCompanyTools {

    private final NamedCompanyResolver resolver;
    private final StrategyService strategies;
    private final int maxNames;

    public NamedCompanyTools(NamedCompanyResolver resolver, StrategyService strategies,
                             LightMoveProperties properties) {
        this.resolver = resolver;
        this.strategies = strategies;
        this.maxNames = properties.assistant().maxNamesPerLookup();
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
        NamedCompanyResolver.Resolution resolution = resolver.resolve(asked, country, offLimits);
        List<NamedCompanyFinding> findings = new ArrayList<>();
        for (NamedCompanyResolver.ResolvedName one : resolution.names()) {
            findings.add(one.finding());
            record(recorder, one.finding(), one.details());
        }
        recorder.countVendorSearches(resolution.vendorSearches());

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

    private List<NamedCompanyRequest> requested(List<NamedCompanyRequest> companies) {
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
                .limit(maxNames)
                .toList();
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
}
