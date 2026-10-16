package app.uncava.api.position.service;

import app.uncava.api.common.constant.CompetencyPanel;
import app.uncava.api.core.audit.constant.ProjectEventType;
import app.uncava.api.core.audit.service.AuditService;
import app.uncava.api.core.error.constant.ErrorCode;
import app.uncava.api.core.error.model.ApiException;
import app.uncava.api.position.constant.FieldSource;
import app.uncava.api.position.constant.PositionFieldKeys;
import app.uncava.api.position.dto.CompensationDto;
import app.uncava.api.position.dto.PositionResponse;
import app.uncava.api.position.dto.PutCompensationRequest;
import app.uncava.api.position.dto.PutCompetenciesRequest;
import app.uncava.api.position.dto.PutCriteriaRequest;
import app.uncava.api.position.dto.PutMandateContextRequest;
import app.uncava.api.position.dto.PutPositionDetailsRequest;
import app.uncava.api.position.dto.PutReportingStructureRequest;
import app.uncava.api.position.dto.ResponsibilityDto;
import app.uncava.api.position.dto.StrategicPriorityDto;
import app.uncava.api.position.model.AssessmentBrief;
import app.uncava.api.position.model.BriefCompetencies;
import app.uncava.api.position.model.BriefCompetency;
import app.uncava.api.position.model.BriefCriterion;
import app.uncava.api.position.model.CompensationPackage;
import app.uncava.api.position.model.MandateContext;
import app.uncava.api.position.model.Position;
import app.uncava.api.position.model.PositionBenefit;
import app.uncava.api.position.model.PositionCompetency;
import app.uncava.api.position.model.PositionCriterion;
import app.uncava.api.position.model.PositionDetails;
import app.uncava.api.position.model.PositionOrgNode;
import app.uncava.api.position.model.PositionPriority;
import app.uncava.api.position.model.PositionResponsibility;
import app.uncava.api.position.model.ReportingStructure;
import app.uncava.api.position.repository.PositionRepository;
import app.uncava.api.positiontemplate.model.PositionTemplate;
import app.uncava.api.positiontemplate.service.PositionTemplateService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The position brief behind a mandate: one read, one write per step, and the publication stamp.
 *
 * <p>Writes are deliberately lenient: autosave must persist a half-typed step, so nothing refuses an
 * inverted salary band or a panel that does not total 100.
 */
@Service
@RequiredArgsConstructor
public class PositionService {

    private final PositionBriefLoader briefs;
    private final PositionRepository positions;
    private final PositionResponseAssembler assembler;
    private final PositionTemplateService templates;
    private final AuditService audit;

    @Transactional
    public PositionResponse get(UUID workspaceId, UUID projectId) {
        return assembler.assemble(briefs.require(workspaceId, projectId));
    }

    /**
     * For a reader that must not write (a client seat reads the report): unlike {@link #get}, which
     * drafts and saves a missing brief, this persists nothing.
     */
    @Transactional(readOnly = true)
    public CompensationDto compensationOf(UUID workspaceId, UUID projectId) {
        Position position = briefs.find(workspaceId, projectId)
                .orElseGet(() -> Position.forProject(projectId, null));
        return assembler.compensationOf(position);
    }

    /**
     * The brief's competencies alone, for the candidate scorecard: the one read another feature takes of the
     * assessment step, so it is bound to these two records rather than the brief's DTOs. Like
     * {@link #compensationOf}, it never drafts a brief.
     */
    @Transactional(readOnly = true)
    public BriefCompetencies competenciesOf(UUID workspaceId, UUID projectId) {
        return competenciesOf(briefs.find(workspaceId, projectId)
                .orElseGet(() -> Position.forProject(projectId, null)));
    }

    /** The brief as the AI assessment reads it, through the same narrow seam; it never drafts a brief either. */
    @Transactional(readOnly = true)
    public AssessmentBrief assessmentBriefOf(UUID workspaceId, UUID projectId) {
        PositionBrief brief = briefs.read(workspaceId, projectId);
        Position position = brief.position();
        return new AssessmentBrief(
                brief.project().getPositionTitle(),
                position.getSeniority(),
                position.getDepartment(),
                position.getLocationCity(),
                position.getLocationCountry(),
                position.getNarrative(),
                position.getResponsibilities().stream().map(PositionResponsibility::getText).toList(),
                position.getBusinessDriver(),
                position.getStrategicPriorities().stream()
                        .filter(PositionPriority::isSelected)
                        .map(PositionPriority::getName)
                        .toList(),
                position.getCriteria().stream()
                        .map(criterion -> new BriefCriterion(criterion.getText(), criterion.getMode()))
                        .toList(),
                competenciesOf(position));
    }

    private static BriefCompetencies competenciesOf(Position position) {
        return new BriefCompetencies(
                position.getCompetencies().stream()
                        .map(competency -> new BriefCompetency(competency.getPanel(), competency.getName(),
                                competency.getDescription(), competency.getWeight()))
                        .toList(),
                position.getTechnicalShare());
    }

    /** For a reader that must not write: an undrafted brief reads blank. */
    @Transactional(readOnly = true)
    public PositionResponse briefOf(UUID workspaceId, UUID projectId) {
        return assembler.assemble(briefs.read(workspaceId, projectId));
    }

    @Transactional
    public PositionResponse putDetails(UUID userId, UUID workspaceId, UUID projectId,
                                       PutPositionDetailsRequest request, HttpServletRequest httpRequest) {
        PositionBrief brief = briefs.require(workspaceId, projectId);
        brief.position().applyDetails(new PositionDetails(
                request.department(), request.locationCity(), request.locationCountry(), request.employmentType(),
                request.seniority(), responsibilitiesOf(request.responsibilities()), request.narrative(),
                fieldSourcesOf(request.fieldSources(), PositionFieldKeys.DETAILS)));
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
                request.confidential(), request.internalContext(),
                fieldSourcesOf(request.fieldSources(), PositionFieldKeys.CONTEXT)));
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
                                node.canvasX(), node.canvasY(), FieldSource.orManual(node.source())))
                        .toList(),
                request.teamSize(), request.noticeValue(), request.noticeUnit(),
                fieldSourcesOf(request.fieldSources(), PositionFieldKeys.REPORTING)));
        return saved(brief, userId, workspaceId, projectId, "reporting", httpRequest);
    }

    @Transactional
    public PositionResponse putCompensation(UUID userId, UUID workspaceId, UUID projectId,
                                            PutCompensationRequest request, HttpServletRequest httpRequest) {
        if (request.bonusBasis() != null && !request.bonusBasis().admits(request.bonusValue())) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "bonusValue", request.bonusBasis().ceilingMessage());
        }
        PositionBrief brief = briefs.require(workspaceId, projectId);
        brief.position().applyCompensation(new CompensationPackage(
                request.currency(), request.salaryMin(), request.salaryMax(), request.baseSalaryMode(),
                request.bonusValue(), request.bonusBasis(),
                request.incentiveType(), request.incentiveAmount(), request.incentiveVesting(),
                orEmpty(request.benefits()).stream()
                        .map(benefit -> PositionBenefit.of(benefit.name(), benefit.amount(),
                                benefit.frequency(), FieldSource.orManual(benefit.source())))
                        .toList()));
        return saved(brief, userId, workspaceId, projectId, "compensation", httpRequest);
    }

    @Transactional
    public PositionResponse putCriteria(UUID userId, UUID workspaceId, UUID projectId,
                                        PutCriteriaRequest request, HttpServletRequest httpRequest) {
        PositionBrief brief = briefs.require(workspaceId, projectId);
        brief.position().replaceCriteria(request.criteria().stream()
                .map(criterion -> PositionCriterion.of(
                        criterion.text(), criterion.mode(), FieldSource.orManual(criterion.source())))
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
                                        competency.name(), competency.description(), competency.weight(),
                                        FieldSource.orManual(competency.source()))),
                        request.behavioural().stream()
                                .map(competency -> PositionCompetency.of(CompetencyPanel.BEHAVIOURAL,
                                        competency.name(), competency.description(), competency.weight(),
                                        FieldSource.orManual(competency.source()))))
                .toList(), request.technicalShare());
        return saved(brief, userId, workspaceId, projectId, "competencies", httpRequest);
    }

    /** Not a lock (V38): every step stays writable, and republishing keeps the original stamp. */
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

    /** Re-drafts the brief from a picked template; what survives is {@link PositionTemplateApplier}'s contract. */
    @Transactional
    public PositionResponse applyTemplate(UUID userId, UUID workspaceId, UUID projectId,
                                          UUID templateId, HttpServletRequest httpRequest) {
        PositionBrief brief = briefs.require(workspaceId, projectId);
        PositionTemplate template = templates.require(workspaceId, templateId);
        PositionTemplateApplier.applyTo(brief.position(), template);

        audit.projectEvent(ProjectEventType.POSITION_TEMPLATE_APPLIED, userId, workspaceId, projectId, httpRequest)
                .detail("template", template.getCode())
                .record();
        return assembler.assemble(brief);
    }

    /** Of these mandates, already scoped to one workspace by the caller, the ones whose brief somebody has worked on. */
    @Transactional(readOnly = true)
    public Set<UUID> projectsWithBriefWorkedOn(Collection<UUID> projectIds) {
        return projectIds.isEmpty() ? Set.of() : Set.copyOf(positions.findProjectIdsWorkedOn(projectIds));
    }

    /** Drafts the brief for a newly created mandate, from the template matched on its role title. */
    @Transactional
    public Position seedFor(UUID workspaceId, UUID projectId, String positionTitle, String hqCountry) {
        return briefs.draft(workspaceId, projectId, positionTitle, hqCountry);
    }

    private PositionResponse saved(PositionBrief brief, UUID userId, UUID workspaceId, UUID projectId,
                                   String section, HttpServletRequest httpRequest) {
        auditChange(ProjectEventType.POSITION_UPDATED, userId, workspaceId, projectId, section, httpRequest);
        return assembler.assemble(brief);
    }

    private void auditChange(ProjectEventType event, UUID userId, UUID workspaceId, UUID projectId,
                             String section, HttpServletRequest httpRequest) {
        audit.projectEvent(event, userId, workspaceId, projectId, httpRequest)
                .detailIfPresent("section", section)
                .record();
    }

    /** A duplicate name is refused rather than quietly de-duplicated; since V40 the schema no longer prevents it. */
    private static List<PositionPriority> prioritiesOf(List<StrategicPriorityDto> sent) {
        List<PositionPriority> priorities = orEmpty(sent).stream()
                .map(priority -> PositionPriority.of(priority.name(), priority.selected(),
                        FieldSource.orManual(priority.source())))
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

    private static List<PositionResponsibility> responsibilitiesOf(List<ResponsibilityDto> sent) {
        return orEmpty(sent).stream()
                .map(responsibility -> PositionResponsibility.of(
                        responsibility.text(), FieldSource.orManual(responsibility.source())))
                .toList();
    }

    /** No {@code fieldSources} at all is read as a person having typed the whole step: every key {@code MANUAL}. */
    private static Map<String, FieldSource> fieldSourcesOf(Map<String, FieldSource> sent,
                                                            Set<String> allowedKeys) {
        if (sent == null) {
            return PositionFieldKeys.allManual(allowedKeys);
        }
        PositionFieldKeys.requireKnown(sent, allowedKeys);
        return sent;
    }

    private static <T> List<T> orEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }
}
