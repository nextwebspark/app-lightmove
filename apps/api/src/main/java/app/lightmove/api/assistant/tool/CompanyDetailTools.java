package app.lightmove.api.assistant.tool;

import app.lightmove.api.common.industry.model.ResolvedIndustry;
import app.lightmove.api.common.industry.service.Industries;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
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
    static final int MAX_ABOUT = 160;
    static final int NICHE_SHOWN = 8;

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
        int step = recorder.startStep("Reading " + asked.size() + (asked.size() == 1 ? " company" : " companies"));

        Map<String, CompanyRow> rows = market.byAccountIds(asked).stream()
                .collect(Collectors.toMap(CompanyRow::apolloAccountId, Function.identity(), (first, second) -> first));
        Map<String, Long> nicheCounts = market.nicheKeywordCounts(rows.values().stream()
                .flatMap(row -> row.keywords().stream()).toList());
        List<CompanyDetail> companies = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        for (String key : asked) {
            CompanyRow row = rows.get(key);
            Optional<CompanyDetail> detail = row != null
                    ? Optional.of(fromRow(row, nicheCounts))
                    : pageOf(key, recorder);
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
            the first 100 in name order and total, how many the stage holds.""")
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
        Map<String, CompanyRow> rows = market.byAccountIds(filed.companies().stream()
                        .map(TriageCompanyResponse::apolloAccountId).filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(CompanyRow::apolloAccountId, Function.identity(), (first, second) -> first));
        Map<String, Long> nicheCounts = market.nicheKeywordCounts(rows.values().stream()
                .flatMap(row -> row.keywords().stream()).toList());
        List<CompanyDetail> companies = filed.companies().stream()
                .map(company -> fromFiled(company, rows.get(company.apolloAccountId()), nicheCounts)
                        .inMandateAs(status.value()))
                .toList();

        recorder.finishStep(step, filed.totalCount() > companies.size()
                ? "The first " + companies.size() + " of " + filed.totalCount()
                : companies.size() + (companies.size() == 1 ? " company" : " companies"));
        return new MandateCompanies(status.value(), filed.totalCount(), companies);
    }

    /** A LinkedIn page an earlier answer researched: the vendor cache, else what that answer's card kept. */
    private Optional<CompanyDetail> pageOf(String key, TurnRecorder recorder) {
        String slug = Optional.ofNullable(LinkedInUrls.companySlugOrNull(key)).orElse(key.toLowerCase(Locale.ROOT));
        Optional<VendorCompanyRecord> held = research.heldRecordOf(slug);
        if (held.isPresent()) {
            VendorCompanyRecord page = held.get();
            String industry = page.asCapturedDetails().map(CapturedCompanyDetails::industry).orElse(page.industry());
            return Optional.of(new CompanyDetail(null, slug, page.companyName(), industry, sectorOf(industry),
                    page.companyCountry(), page.companyCity(), page.employeesInLinkedin(), page.foundedYear(),
                    shortened(page.about()), page.keywords() == null ? List.of()
                            : page.keywords().stream().limit(NICHE_SHOWN).toList(), null));
        }
        CapturedCompanyDetails kept = recorder.proposablePages().get(slug);
        if (kept == null) {
            return Optional.empty();
        }
        return Optional.of(new CompanyDetail(null, slug, kept.companyName(), kept.industry(),
                sectorOf(kept.industry()), kept.companyCountry(), kept.companyCity(), kept.numEmployees(),
                kept.foundedYear(), shortened(kept.shortDescription()), List.of(), null));
    }

    private CompanyDetail fromRow(CompanyRow row, Map<String, Long> nicheCounts) {
        return new CompanyDetail(row.apolloAccountId(), LinkedInUrls.companySlugOrNull(row.companyLinkedinUrl()),
                row.companyName(), row.industry(), sectorOf(row.industry()), row.companyCountry(),
                row.companyCity(), row.numEmployees(), row.foundedYear(), shortened(row.shortDescription()),
                ApolloCompanyQueryService.distinctiveOf(row.keywords(), nicheCounts, NICHE_SHOWN), null);
    }

    /** The mandate's own snapshot, with the database's niche where the row came from it. */
    private CompanyDetail fromFiled(TriageCompanyResponse company, CompanyRow row, Map<String, Long> nicheCounts) {
        List<String> niche = row == null ? List.of()
                : ApolloCompanyQueryService.distinctiveOf(row.keywords(), nicheCounts, NICHE_SHOWN);
        return new CompanyDetail(company.apolloAccountId(),
                LinkedInUrls.companySlugOrNull(company.companyLinkedinUrl()), company.companyName(),
                company.industry(), sectorOf(company.industry()), company.companyCountry(), company.companyCity(),
                company.numEmployees(), company.foundedYear(), shortened(company.shortDescription()), niche, null);
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

    /** "shortlisted", "Shortlisted", "SHORTLISTED" and "in universe" alike; the universe when nothing fits. */
    static TriageCompanyStatus stageOf(String stage) {
        String asked = stage == null ? "" : stage.replaceAll("[^A-Za-z]", "").toLowerCase(Locale.ROOT);
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

    private static String sectorOf(String industry) {
        ResolvedIndustry resolved = Industries.resolve(industry);
        return resolved == null ? null : resolved.sectorGroup();
    }

    private static String shortened(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String flattened = text.replaceAll("\\s+", " ").strip();
        return flattened.length() <= MAX_ABOUT ? flattened : flattened.substring(0, MAX_ABOUT) + "…";
    }
}
