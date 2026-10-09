package app.lightmove.api.assistant.tool;

import app.lightmove.api.core.text.service.LinkedInUrls;
import app.lightmove.api.enrichment.company.model.VendorCompanyRecord;
import app.lightmove.api.enrichment.company.service.CompanyResearch;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.dto.TriageCompaniesResponse;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import app.lightmove.api.triagecompany.model.MandateStages;
import app.lightmove.api.triagecompany.model.TriageCompanyFilters;
import app.lightmove.api.triagecompany.service.TriageCompanyReadService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * What the model judges companies on when it ranks or tiers them: the companies of an earlier answer by
 * their keys, or a stage of the mandate. Reads only what is held — the company database, the vendor
 * cache, the mandate's own rows — so no read here is ever billed.
 */
@Component
@RequiredArgsConstructor
public class CompanyDetailTools {

    static final int MAX_KEYS = 50;
    static final int MAX_MANDATE_COMPANIES = 100;

    private static final Pattern NOT_A_LETTER = Pattern.compile("[^A-Za-z]");

    private final ApolloCompanyQueryService market;
    private final CompanyResearch research;
    private final TriageCompanyReadService triaged;

    @Tool(description = """
            Read what companies do — industry, sector, country, city, headcount, founding year, a short \
            about and their niche keywords — to rank, tier or compare them. Pass the keys of an earlier \
            answer's <suggested_companies> rows (the apolloAccountId or linkedinSlug after the bracket), \
            up to 50, in any order; they come back in the order given. notFound lists keys nothing is \
            known about.""")
    public CompanyDetails readCompanyDetails(
            @ToolParam(description = "The companies' keys, as the earlier rows give them") List<String> keys,
            ToolContext toolContext) {
        AssistantToolContext context = AssistantToolContext.from(toolContext);
        TurnRecorder recorder = context.recorder();
        List<String> asked = keys == null ? List.of() : keys.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(key -> !key.isEmpty())
                .distinct()
                .limit(MAX_KEYS)
                .toList();
        int step = recorder.startStep("Reading " + companies(asked.size()));

        Map<String, CompanyRow> rows = byAccountId(asked);
        Map<String, Long> nicheCounts = nicheCountsOf(rows.values());
        Map<String, String> slugs = asked.stream()
                .filter(key -> !rows.containsKey(key))
                .collect(Collectors.toMap(Function.identity(), CompanyDetailTools::slugOf, (first, second) -> first));
        Map<String, VendorCompanyRecord> pages = research.heldRecordsOf(slugs.values());
        Map<String, CapturedCompanyDetails> kept = recorder.proposablePages();
        List<CompanyDetail> companies = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        for (String key : asked) {
            CompanyRow row = rows.get(key);
            String slug = slugs.get(key);
            Optional<CompanyDetail> detail = row != null
                    ? Optional.of(CompanyDetail.ofRow(row, nicheOf(row, nicheCounts)))
                    : Optional.ofNullable(pages.get(slug)).map(CompanyDetail::ofPage)
                            .or(() -> Optional.ofNullable(kept.get(slug))
                                    .map(page -> CompanyDetail.ofKept(slug, page)));
            detail.ifPresentOrElse(companies::add, () -> notFound.add(key));
        }

        List<CompanyDetail> staged = withStages(context, companies);
        recorder.finishStep(step, staged.size() + " read"
                + (notFound.isEmpty() ? "" : " · " + notFound.size() + " not found"));
        return new CompanyDetails(staged, List.copyOf(notFound));
    }

    @Tool(description = """
            Read the companies this mandate has already filed at one stage — "my shortlist", "the \
            universe", "the declined ones" — with what each does, to rank, tier or compare them. Answers \
            the stage it read, the first 100 in name order, and total, how many the stage holds.""")
    public MandateCompanies listMandateCompanies(
            @ToolParam(required = false, description = "inUniverse, shortlisted or declined; inUniverse when omitted")
            String stage,
            ToolContext toolContext) {
        AssistantToolContext context = AssistantToolContext.from(toolContext);
        TurnRecorder recorder = context.recorder();
        TriageCompanyStatus status = stageOf(stage);
        int step = recorder.startStep("Reading the " + labelOf(status) + " companies");

        TriageCompaniesResponse filed = triaged.listAllOfStage(context.workspaceId(), context.projectId(), status,
                TriageCompanyFilters.none(), MAX_MANDATE_COMPANIES);
        Map<String, CompanyRow> rows = byAccountId(filed.companies().stream()
                .map(TriageCompanyResponse::apolloAccountId).filter(Objects::nonNull).toList());
        Map<String, Long> nicheCounts = nicheCountsOf(rows.values());
        List<CompanyDetail> companies = filed.companies().stream()
                .map(company -> {
                    CompanyRow row = rows.get(company.apolloAccountId());
                    return CompanyDetail.ofFiled(company, row == null ? List.of() : nicheOf(row, nicheCounts),
                            status.value());
                })
                .toList();

        recorder.finishStep(step, filed.totalCount() > companies.size()
                ? "The first " + companies.size() + " of " + filed.totalCount()
                : companies(companies.size()));
        return new MandateCompanies(status.value(), filed.totalCount(), companies);
    }

    private Map<String, CompanyRow> byAccountId(List<String> apolloAccountIds) {
        return market.byAccountIds(apolloAccountIds).stream()
                .collect(Collectors.toMap(CompanyRow::apolloAccountId, Function.identity(), (first, second) -> first));
    }

    private Map<String, Long> nicheCountsOf(Collection<CompanyRow> rows) {
        return market.nicheKeywordCounts(rows.stream().flatMap(row -> row.keywords().stream()).toList());
    }

    private static List<String> nicheOf(CompanyRow row, Map<String, Long> nicheCounts) {
        return ApolloCompanyQueryService.distinctiveOf(row.keywords(), nicheCounts, CompanyDetail.NICHE_SHOWN);
    }

    private List<CompanyDetail> withStages(AssistantToolContext context, List<CompanyDetail> companies) {
        MandateStages stages = triaged.stagesOf(context.workspaceId(), context.projectId(),
                companies.stream().map(CompanyDetail::apolloAccountId).filter(Objects::nonNull).toList(),
                companies.stream().map(CompanyDetail::companyName).toList());
        return companies.stream()
                .map(company -> company.inMandateAs(
                        stages.stageTokenOf(company.apolloAccountId(), company.companyName())))
                .toList();
    }

    private static String slugOf(String key) {
        return Optional.ofNullable(LinkedInUrls.companySlugOrNull(key)).orElse(key.toLowerCase(Locale.ROOT));
    }

    /** "shortlisted", "Shortlisted", "SHORTLISTED" and "in universe" alike; the universe when nothing fits. */
    static TriageCompanyStatus stageOf(String stage) {
        String asked = stage == null ? "" : NOT_A_LETTER.matcher(stage).replaceAll("").toLowerCase(Locale.ROOT);
        return Arrays.stream(TriageCompanyStatus.values())
                .filter(status -> status.value().toLowerCase(Locale.ROOT).equals(asked))
                .findFirst()
                .orElse(TriageCompanyStatus.IN_UNIVERSE);
    }

    private static String labelOf(TriageCompanyStatus status) {
        return switch (status) {
            case IN_UNIVERSE -> "in-universe";
            case SHORTLISTED -> "shortlisted";
            case DECLINED -> "declined";
        };
    }

    private static String companies(int count) {
        return count + (count == 1 ? " company" : " companies");
    }
}
