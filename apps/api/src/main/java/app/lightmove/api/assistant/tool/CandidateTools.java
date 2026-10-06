package app.lightmove.api.assistant.tool;

import app.lightmove.api.candidate.constant.CandidateStatus;
import app.lightmove.api.candidate.dto.CandidateListCriteria;
import app.lightmove.api.candidate.dto.CandidatesResponse;
import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.model.TriageCompanyMatches;
import app.lightmove.api.triagecompany.service.TriageCompanyReadService;
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
            page or narrow the question rather than reporting the list as everyone. When \
            companyNameTooBroad is true the name matched more companies than one read takes: say so \
            and ask for a fuller name rather than reporting the count as complete.""")
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

        TriageCompanyMatches named = blankToNull(companyName) == null ? null
                : triaged.named(context.workspaceId(), context.projectId(), companyName.strip(),
                        MAX_MATCHED_COMPANIES);
        List<UUID> companyIds = named == null ? null
                : named.companies().stream().map(TriageCompanyResponse::id).toList();
        CandidatesResponse found = candidates.list(context.workspaceId(), context.projectId(),
                new CandidateListCriteria(companyIds, null, blankToNull(executiveName),
                        asked == null ? null : asked.value(), pageAsked, maxRows));
        List<MappedExecutiveSummary> executives = found.candidates().stream()
                .map(MappedExecutiveSummary::of)
                .toList();
        recorder.finishStep(step, found.totalCount() + (found.totalCount() == 1 ? " executive" : " executives"));
        return new MappedExecutives(found.totalCount(), pageAsked, executives.size(),
                named != null && named.truncated(), executives);
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
        UUID id = uuidOrNull(candidateId);
        CandidateDossier dossier = id == null ? null
                : candidates.dossierOf(context.workspaceId(), context.projectId(), id).orElse(null);
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
        Set<UUID> mapped = candidates.companiesWithExecutivesOf(context.workspaceId(), context.projectId());
        TriageCompanyMatches unmapped = triaged.ofStageExcluding(context.workspaceId(), context.projectId(),
                TriageCompanyStatus.IN_UNIVERSE, mapped, maxRows);
        long inUniverse = triaged.countOfStage(context.workspaceId(), context.projectId(),
                TriageCompanyStatus.IN_UNIVERSE);
        recorder.finishStep(step, unmapped.totalCount() + " of " + inUniverse + " with nobody mapped");
        return new UnmappedCompanies(unmapped.totalCount(), inUniverse,
                unmapped.companies().stream().map(TriageCompanyResponse::companyName).toList());
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
