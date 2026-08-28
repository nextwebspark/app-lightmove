package app.lightmove.api.position.service;

import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.position.constant.CompetencyPanel;
import app.lightmove.api.position.dto.PositionResponse;
import app.lightmove.api.position.dto.PutCompensationRequest;
import app.lightmove.api.position.dto.PutCompetenciesRequest;
import app.lightmove.api.position.dto.PutCriteriaRequest;
import app.lightmove.api.position.dto.PutMandateContextRequest;
import app.lightmove.api.position.dto.PutPositionDetailsRequest;
import app.lightmove.api.position.dto.PutReportingStructureRequest;
import app.lightmove.api.position.dto.StrategicPriorityDto;
import app.lightmove.api.position.model.CompensationPackage;
import app.lightmove.api.position.model.MandateContext;
import app.lightmove.api.position.model.Position;
import app.lightmove.api.position.model.PositionBenefit;
import app.lightmove.api.position.model.PositionCompetency;
import app.lightmove.api.position.model.PositionCriterion;
import app.lightmove.api.position.model.PositionDetails;
import app.lightmove.api.position.model.PositionOrgNode;
import app.lightmove.api.position.model.PositionPriority;
import app.lightmove.api.position.model.ReportingStructure;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The position brief behind a mandate: one read, one write per wizard step, and the publication stamp.
 *
 * <p><b>A step is the write unit because a step is the edit unit.</b> The screen has no Save button —
 * it autosaves whatever section is in front of the consultant — so a single whole-document write would
 * resend five untouched steps on every keystroke, and one slip in serialising any of them would blank
 * a section nobody was editing.
 *
 * <p><b>Writes are deliberately lenient.</b> Autosave must be free to persist a half-typed step, so
 * nothing here refuses a band whose minimum exceeds its maximum or a panel that does not total 100.
 * Those are readings the screen offers, not conditions of storing what somebody wrote down.
 *
 * <p>Two of the fields the screen shows are the mandate's, not the brief's. Step one edits the role
 * title, so that step writes through to the project row it was loaded with. The one target date (V8)
 * is read-only here — every read returns the project's, and no step writes it.
 */
@Service
@RequiredArgsConstructor
public class PositionService {

    private final PositionBriefLoader briefs;
    private final PositionResponseAssembler assembler;
    private final AuditService audit;

    @Transactional
    public PositionResponse get(UUID workspaceId, UUID projectId) {
        return assembler.assemble(briefs.require(workspaceId, projectId));
    }

    @Transactional
    public PositionResponse putDetails(UUID userId, UUID workspaceId, UUID projectId,
                                       PutPositionDetailsRequest request, HttpServletRequest httpRequest) {
        PositionBrief brief = briefs.require(workspaceId, projectId);
        brief.position().applyDetails(new PositionDetails(
                request.department(), request.location(), request.employmentType(),
                request.seniority(), orEmpty(request.responsibilities()), request.narrative()));
        // The mandate keeps one role title, on the project — the step's "Role title" writes it there.
        brief.project().rename(request.roleTitle());
        return saved(brief, userId, workspaceId, projectId, "details", httpRequest);
    }

    @Transactional
    public PositionResponse putContext(UUID userId, UUID workspaceId, UUID projectId,
                                       PutMandateContextRequest request, HttpServletRequest httpRequest) {
        PositionBrief brief = briefs.require(workspaceId, projectId);
        brief.position().applyContext(new MandateContext(
                request.mandateReason(), request.businessDriver(),
                prioritiesOf(request.strategicPriorities()),
                request.confidential(), request.internalContext()));
        return saved(brief, userId, workspaceId, projectId, "context", httpRequest);
    }

    @Transactional
    public PositionResponse putReporting(UUID userId, UUID workspaceId, UUID projectId,
                                         PutReportingStructureRequest request, HttpServletRequest httpRequest) {
        OrgChartRules.validate(request.orgChart());
        PositionBrief brief = briefs.require(workspaceId, projectId);
        brief.position().applyReporting(new ReportingStructure(
                OrgChartRules.withoutUnnamedLeaves(request.orgChart()).stream()
                        .map(node -> PositionOrgNode.of(node.nodeId(), node.parentNodeId(),
                                node.title(), node.name(), node.mandateSeat(),
                                node.canvasX(), node.canvasY()))
                        .toList(),
                request.teamSize(), request.noticeValue(), request.noticeUnit()));
        return saved(brief, userId, workspaceId, projectId, "reporting", httpRequest);
    }

    @Transactional
    public PositionResponse putCompensation(UUID userId, UUID workspaceId, UUID projectId,
                                            PutCompensationRequest request, HttpServletRequest httpRequest) {
        PositionBrief brief = briefs.require(workspaceId, projectId);
        brief.position().applyCompensation(new CompensationPackage(
                request.currency(), request.salaryMin(), request.salaryMax(), request.baseSalaryMode(),
                request.bonusValue(), request.bonusBasis(),
                request.incentiveType(), request.incentiveAmount(), request.incentiveVesting(),
                orEmpty(request.benefits()).stream()
                        .map(benefit -> PositionBenefit.of(
                                benefit.name(), benefit.amount(), benefit.frequency()))
                        .toList()));
        return saved(brief, userId, workspaceId, projectId, "compensation", httpRequest);
    }

    @Transactional
    public PositionResponse putCriteria(UUID userId, UUID workspaceId, UUID projectId,
                                        PutCriteriaRequest request, HttpServletRequest httpRequest) {
        PositionBrief brief = briefs.require(workspaceId, projectId);
        brief.position().replaceCriteria(request.criteria().stream()
                .map(criterion -> PositionCriterion.of(
                        criterion.text(), criterion.mode(), criterion.fromBrief()))
                .toList());
        return saved(brief, userId, workspaceId, projectId, "criteria", httpRequest);
    }

    @Transactional
    public PositionResponse putCompetencies(UUID userId, UUID workspaceId, UUID projectId,
                                            PutCompetenciesRequest request, HttpServletRequest httpRequest) {
        PositionBrief brief = briefs.require(workspaceId, projectId);
        brief.position().replaceCompetencies(Stream.concat(
                        request.technical().stream()
                                .map(competency -> PositionCompetency.of(CompetencyPanel.TECHNICAL,
                                        competency.name(), competency.description(), competency.weight())),
                        request.behavioural().stream()
                                .map(competency -> PositionCompetency.of(CompetencyPanel.BEHAVIOURAL,
                                        competency.name(), competency.description(), competency.weight())))
                .toList());
        return saved(brief, userId, workspaceId, projectId, "competencies", httpRequest);
    }

    /**
     * Records that somebody declared the brief ready. Not a lock — V38 retired that — so every step
     * above stays writable afterwards, and republishing keeps the original stamp rather than moving it.
     */
    @Transactional
    public PositionResponse publish(UUID userId, UUID workspaceId, UUID projectId,
                                    HttpServletRequest httpRequest) {
        PositionBrief brief = briefs.require(workspaceId, projectId);
        boolean wasAlreadyPublished = brief.position().isPublished();
        brief.position().publish(userId);
        if (!wasAlreadyPublished) {
            auditChange(ProjectEventType.POSITION_PUBLISHED, userId, workspaceId, projectId, null, httpRequest);
        }
        return assembler.assemble(brief);
    }

    @Transactional
    public PositionResponse withdrawPublication(UUID userId, UUID workspaceId, UUID projectId,
                                                HttpServletRequest httpRequest) {
        PositionBrief brief = briefs.require(workspaceId, projectId);
        if (brief.position().isPublished()) {
            brief.position().withdrawPublication();
            auditChange(ProjectEventType.POSITION_PUBLICATION_WITHDRAWN,
                    userId, workspaceId, projectId, null, httpRequest);
        }
        return assembler.assemble(brief);
    }

    /**
     * Drafts the brief for a mandate that has just been created, from the template matched on its role
     * title. Takes primitives rather than the {@code Project} it belongs to: the mandate is
     * {@code project}'s to own, and a brief only needs three facts about it.
     */
    @Transactional
    public Position seedFor(UUID projectId, String positionTitle, String location) {
        return briefs.draft(projectId, positionTitle, location);
    }

    private PositionResponse saved(PositionBrief brief, UUID userId, UUID workspaceId, UUID projectId,
                                   String section, HttpServletRequest httpRequest) {
        auditChange(ProjectEventType.POSITION_UPDATED, userId, workspaceId, projectId, section, httpRequest);
        return assembler.assemble(brief);
    }

    private void auditChange(ProjectEventType event, UUID userId, UUID workspaceId, UUID projectId,
                             String section, HttpServletRequest httpRequest) {
        AuditService.Builder entry = audit.event(event)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest);
        if (section != null) {
            entry.detail("section", section);
        }
        entry.record();
    }

    /**
     * One chip per name, checked here rather than left to the screen.
     *
     * <p>Until V40 the priorities were a set keyed on the value, so the schema made a duplicate
     * impossible; an ordered list of names cannot say the same thing, and two chips reading alike are
     * indistinguishable on the screen and meaningless in the brief. Refused rather than quietly
     * de-duplicated: a caller that sent both meant something by it, and dropping one silently would
     * answer with a brief it did not ask for.
     */
    private static List<PositionPriority> prioritiesOf(List<StrategicPriorityDto> sent) {
        List<PositionPriority> priorities = orEmpty(sent).stream()
                .map(priority -> PositionPriority.of(priority.name(), priority.selected()))
                .toList();
        long distinct = priorities.stream()
                .map(priority -> priority.getName().toLowerCase(Locale.ROOT))
                .distinct()
                .count();
        if (distinct != priorities.size()) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "Two strategic priorities share a name");
        }
        return priorities;
    }

    private static <T> List<T> orEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }
}
