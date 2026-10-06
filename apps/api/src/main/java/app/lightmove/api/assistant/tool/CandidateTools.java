package app.lightmove.api.assistant.tool;

import app.lightmove.api.candidate.constant.CandidateStatus;
import app.lightmove.api.candidate.dto.CandidateListCriteria;
import app.lightmove.api.candidate.dto.CandidatesResponse;
import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.dto.TriageCompaniesResponse;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.model.TriageCompanyFilters;
import app.lightmove.api.triagecompany.service.TriageCompanyReadService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Reads the executives this mandate has mapped. Read-only: nothing here files, changes or contacts
 * anyone. Every row leaves through {@link MappedExecutiveSummary} or {@link CandidateDossier}, the two
 * allowlists of what a model may see about a person.
 */
@Component
public class CandidateTools {

    /** Companies one name filter may resolve to — the candidate list refuses more ids than a page holds. */
    private static final int MAX_MATCHED_COMPANIES = 50;
    private static final int MAX_UNIVERSE_READ = 2_000;

    /** {@code CandidateStatus}'s tokens — a literal, since an annotation takes nothing else. */
    static final String STATUSES = "identified, contacted, engaged, interested, notInterested, offLimits, outOfScope";

    private final CandidateService candidates;
    private final TriageCompanyReadService triaged;
    private final int maxRows;

    public CandidateTools(CandidateService candidates, TriageCompanyReadService triaged,
                          LightMoveProperties properties) {
        this.candidates = candidates;
        this.triaged = triaged;
        this.maxRows = properties.assistant().toolRowLimit();
    }

    @Tool(description = """
            List the executives this mandate has mapped, first mapped first, with their title, \
            company, seniority, status and location. Every argument is optional. The answer says \
            how many matched in total and how many are shown — when those differ, ask for the next \
            page or narrow the question rather than reporting the list as everyone.""")
    public MappedExecutives listMappedExecutives(
            @ToolParam(required = false, description = "All or part of the company name they are mapped at")
            String companyName,
            @ToolParam(required = false, description = "All or part of the executive's name")
            String executiveName,
            @ToolParam(required = false, description = "Their status on this mandate, one of: " + STATUSES)
            String status,
            @ToolParam(required = false, description = "Page, from 0") Integer page,
            ToolContext toolContext) {
        AssistantToolContext context = AssistantToolContext.from(toolContext);
        TurnRecorder recorder = context.recorder();
        int step = recorder.startStep(describeList(companyName, executiveName));
        CandidateStatus asked = ApiValueEnum.fromValue(CandidateStatus.class, blankToNull(status));
        int pageAsked = page == null || page < 0 ? 0 : page;

        List<UUID> companyIds = blankToNull(companyName) == null ? null
                : companiesNamed(context, companyName.strip());
        CandidatesResponse found = candidates.list(context.workspaceId(), context.projectId(),
                new CandidateListCriteria(companyIds, null, blankToNull(executiveName),
                        asked == null ? null : asked.value(), pageAsked, maxRows));
        List<MappedExecutiveSummary> executives = found.candidates().stream()
                .map(MappedExecutiveSummary::of)
                .toList();
        recorder.finishStep(step, found.totalCount() + (found.totalCount() == 1 ? " executive" : " executives"));
        return new MappedExecutives(found.totalCount(), pageAsked, executives.size(), executives);
    }

    @Tool(description = """
            Read one mapped executive's profile: their current role, summary, career, education, \
            skills and languages. Takes the candidateId listMappedExecutives returned. Answers null \
            when this mandate has no such executive.""")
    public CandidateDossier readExecutiveProfile(
            @ToolParam(description = "The candidateId from listMappedExecutives") String candidateId,
            ToolContext toolContext) {
        AssistantToolContext context = AssistantToolContext.from(toolContext);
        TurnRecorder recorder = context.recorder();
        int step = recorder.startStep("Reading an executive's profile");
        CandidateDossier dossier = uuidOrNull(candidateId) == null ? null
                : candidates.dossierOf(context.projectId(), uuidOrNull(candidateId)).orElse(null);
        recorder.finishStep(step, dossier == null ? "Not found on this mandate" : dossier.fullName());
        return dossier;
    }

    @Tool(description = """
            List the companies in this mandate's universe with no executive mapped at them yet — \
            where mapping still has to be done. The answer gives the count and up to a page of names.""")
    public UnmappedCompanies companiesWithoutExecutives(ToolContext toolContext) {
        AssistantToolContext context = AssistantToolContext.from(toolContext);
        TurnRecorder recorder = context.recorder();
        int step = recorder.startStep("Finding companies with nobody mapped");
        TriageCompaniesResponse stage = triaged.listAllOfStage(context.workspaceId(), context.projectId(),
                TriageCompanyStatus.IN_UNIVERSE, TriageCompanyFilters.none(), MAX_UNIVERSE_READ);
        List<TriageCompanyResponse> universe = stage.companies();
        Set<UUID> mapped = candidates.companiesWithExecutivesOf(context.workspaceId(), context.projectId());
        List<String> unmapped = universe.stream()
                .filter(company -> !mapped.contains(company.id()))
                .map(TriageCompanyResponse::companyName)
                .toList();
        recorder.finishStep(step, unmapped.size() + " of " + stage.totalCount() + " with nobody mapped");
        return new UnmappedCompanies(unmapped.size(), stage.totalCount(),
                unmapped.subList(0, Math.min(maxRows, unmapped.size())));
    }

    /** An empty list, not null, when nothing matches the name — so the read answers nobody rather than everyone. */
    private List<UUID> companiesNamed(AssistantToolContext context, String companyName) {
        Set<UUID> ids = new LinkedHashSet<>();
        TriageCompanyFilters filters = new TriageCompanyFilters(companyName, null, null);
        for (TriageCompanyStatus stage : TriageCompanyStatus.values()) {
            triaged.listAllOfStage(context.workspaceId(), context.projectId(), stage, filters, MAX_MATCHED_COMPANIES)
                    .companies().forEach(company -> ids.add(company.id()));
        }
        return ids.stream().limit(MAX_MATCHED_COMPANIES).toList();
    }

    private static String describeList(String companyName, String executiveName) {
        if (blankToNull(executiveName) != null) {
            return "Looking for " + executiveName.strip();
        }
        return blankToNull(companyName) == null ? "Listing mapped executives"
                : "Listing executives at " + companyName.strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static UUID uuidOrNull(String value) {
        try {
            return value == null ? null : UUID.fromString(value.strip());
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
    }
}
