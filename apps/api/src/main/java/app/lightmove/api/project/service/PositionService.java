package app.lightmove.api.project.service;

import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.document.service.DocumentTextExtractor;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.llm.model.BriefExtraction;
import app.lightmove.api.core.llm.service.BriefLlmClient;
import app.lightmove.api.project.constant.CompetencyPanel;
import app.lightmove.api.project.constant.CriterionMode;
import app.lightmove.api.project.constant.EmploymentType;
import app.lightmove.api.project.constant.MandateReason;
import app.lightmove.api.project.constant.NoticeUnit;
import app.lightmove.api.project.dto.PositionDtos.BriefDocumentDto;
import app.lightmove.api.project.dto.PositionDtos.CompetencyDto;
import app.lightmove.api.project.dto.PositionDtos.CriterionResponse;
import app.lightmove.api.project.dto.PositionDtos.PositionResponse;
import app.lightmove.api.project.dto.PositionDtos.PutCompetenciesRequest;
import app.lightmove.api.project.dto.PositionDtos.PutCriteriaRequest;
import app.lightmove.api.project.dto.PositionDtos.UpdatePositionRequest;
import app.lightmove.api.project.model.CompetencyRow;
import app.lightmove.api.project.model.Position;
import app.lightmove.api.project.model.PositionCriterion;
import app.lightmove.api.project.model.PositionDetails;
import app.lightmove.api.project.model.PositionDocument;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.repository.ClientRepository;
import app.lightmove.api.project.repository.PositionDocumentRepository;
import app.lightmove.api.project.repository.PositionRepository;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.project.service.PositionTemplates.TemplateSeed;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * The position brief behind a project. Seeded from {@link PositionTemplates} when the project is
 * created (and lazily on first read, for projects that predate the position tables). Every load is
 * scoped through the project's {@code (id, workspaceId)} lookup — the workspace id comes from the
 * principal, so a foreign project 404s before any position row is touched.
 *
 * <p>Locking freezes the whole brief: every write rejects with {@code POSITION_LOCKED} until a
 * project admin unlocks. Readiness (both panels exactly 100%, at least one required criterion) is
 * validated only at lock time — autosave must be free to persist half-balanced panels.
 */
@Service
@RequiredArgsConstructor
public class PositionService {

    /** Sniffed from the multipart part's declared content type — matches the mockup's accept list. */
    private static final Set<String> ALLOWED_DOCUMENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain"
    );

    private final PositionRepository positions;
    private final ProjectRepository projects;
    private final ClientRepository clients;
    private final PositionDocumentRepository documents;
    private final DocumentTextExtractor textExtractor;
    private final BriefLlmClient llm;
    private final LightMoveProperties properties;
    private final AuditService audit;

    @Transactional
    public PositionResponse get(UUID workspaceId, UUID projectId) {
        return toResponse(loadBrief(projectId, workspaceId));
    }

    @Transactional
    public PositionResponse update(UUID userId, UUID workspaceId, UUID projectId,
                                   UpdatePositionRequest request, HttpServletRequest httpRequest) {
        Brief brief = loadUnlockedBrief(projectId, workspaceId);
        brief.position().apply(new PositionDetails(
                request.mandateReason(), request.internalContext(), request.narrative(),
                request.reportsTo(), request.directReports(), request.teamSize(),
                request.location(), request.employmentType(),
                request.salaryMin(), request.salaryMax(), request.currency(),
                request.noticeValue(), request.noticeUnit(), request.bonusTargetPct(), request.ltip(),
                request.benefits() == null ? List.of() : request.benefits(),
                request.confidential()));
        // The mandate's one target date lives on the project — the screen's "Target start" writes here.
        brief.project().setTargetDate(request.startTarget());
        auditPositionChange(userId, workspaceId, projectId, "details", httpRequest);
        return toResponse(brief);
    }

    @Transactional
    public PositionResponse putCriteria(UUID userId, UUID workspaceId, UUID projectId,
                                        PutCriteriaRequest request, HttpServletRequest httpRequest) {
        Brief brief = loadUnlockedBrief(projectId, workspaceId);
        brief.position().replaceCriteria(request.criteria().stream()
                .map(c -> PositionCriterion.of(c.text(), c.mode(), c.fromBrief()))
                .toList());
        auditPositionChange(userId, workspaceId, projectId, "criteria", httpRequest);
        return toResponse(brief);
    }

    @Transactional
    public PositionResponse putCompetencies(UUID userId, UUID workspaceId, UUID projectId,
                                            PutCompetenciesRequest request, HttpServletRequest httpRequest) {
        Brief brief = loadUnlockedBrief(projectId, workspaceId);
        brief.position().replaceCompetencies(Stream.concat(
                        request.technical().stream()
                                .map(c -> CompetencyRow.of(CompetencyPanel.TECHNICAL, c.name(), c.weight())),
                        request.behavioural().stream()
                                .map(c -> CompetencyRow.of(CompetencyPanel.BEHAVIOURAL, c.name(), c.weight())))
                .toList());
        auditPositionChange(userId, workspaceId, projectId, "competencies", httpRequest);
        return toResponse(brief);
    }

    @Transactional
    public PositionResponse lock(UUID userId, UUID workspaceId, UUID projectId,
                                 HttpServletRequest httpRequest) {
        Brief brief = loadBrief(projectId, workspaceId);
        if (brief.position().isLocked()) {
            throw ApiException.of(ErrorCode.POSITION_LOCKED);
        }
        requireReady(brief.position());
        brief.position().lock(userId, Instant.now());
        audit.event(ProjectEventType.POSITION_LOCKED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .record();
        return toResponse(brief);
    }

    @Transactional
    public PositionResponse unlock(UUID userId, UUID workspaceId, UUID projectId,
                                   HttpServletRequest httpRequest) {
        Brief brief = loadBrief(projectId, workspaceId);
        if (!brief.position().isLocked()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "This position is not locked");
        }
        brief.position().unlock();
        audit.event(ProjectEventType.POSITION_UNLOCKED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .record();
        return toResponse(brief);
    }

    /**
     * Uploads a position-description document, extracts its text, and asks the LLM to pull the brief's
     * fields out of it. On success, every field the extraction actually found overwrites the
     * corresponding Position field or list — the document is expected to supersede whatever a generic
     * template seeded, exactly as {@link PositionCriterion#isFromBrief()} already anticipates; a field
     * the extraction didn't find is left exactly as it was. Criteria and competencies are replaced
     * wholesale (marked {@code fromBrief = true}), not merged field-by-field.
     *
     * <p>The whole method is one transaction: if extraction fails partway, nothing is written — not the
     * document row, not a single Position field — so a failed upload is safe to simply retry.
     */
    @Transactional
    public PositionResponse uploadBriefDocument(UUID userId, UUID workspaceId, UUID projectId,
                                                MultipartFile file, HttpServletRequest httpRequest) {
        Brief brief = loadUnlockedBrief(projectId, workspaceId);
        byte[] content = requireValidDocument(file);

        String text = textExtractor.extract(content, file.getContentType());
        BriefExtraction extraction = llm.extractBrief(text, brief.project().getPositionTitle());

        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "Position description";
        PositionDocument document = documents.findByPositionId(brief.position().getId())
                .orElseGet(() -> PositionDocument.forPosition(brief.position().getId()));
        document.replaceWith(fileName, file.getContentType(), file.getSize(), content, userId);
        documents.save(document);

        applyExtraction(brief.position(), extraction);
        auditPositionChange(userId, workspaceId, projectId, "brief-document", httpRequest);
        return toResponse(brief);
    }

    @Transactional
    public BriefDocumentDto getBriefDocument(UUID workspaceId, UUID projectId) {
        Brief brief = loadBrief(projectId, workspaceId);
        return documents.findByPositionId(brief.position().getId()).map(PositionService::documentDto)
                .orElse(null);
    }

    /** Clears the uploaded file only — fields it already populated stay, matching the mockup's Remove. */
    @Transactional
    public PositionResponse removeBriefDocument(UUID userId, UUID workspaceId, UUID projectId,
                                                HttpServletRequest httpRequest) {
        Brief brief = loadUnlockedBrief(projectId, workspaceId);
        documents.deleteByPositionId(brief.position().getId());
        auditPositionChange(userId, workspaceId, projectId, "brief-document-removed", httpRequest);
        return toResponse(brief);
    }

    private byte[] requireValidDocument(MultipartFile file) {
        if (file.isEmpty() || !ALLOWED_DOCUMENT_TYPES.contains(file.getContentType())) {
            throw ApiException.of(ErrorCode.BRIEF_DOCUMENT_UNSUPPORTED_TYPE);
        }
        if (file.getSize() > properties.llm().maxDocumentSize().toBytes()) {
            throw ApiException.of(ErrorCode.BRIEF_DOCUMENT_TOO_LARGE);
        }
        try {
            return file.getBytes();
        } catch (java.io.IOException e) {
            throw new ApiException(ErrorCode.BRIEF_EXTRACTION_FAILED, "Could not read the uploaded file");
        }
    }

    /** Every field the extraction found overwrites the brief; anything it didn't find is left alone. */
    private void applyExtraction(Position position, BriefExtraction extraction) {
        position.apply(new PositionDetails(
                parseOrKeep(MandateReason.class, extraction.mandateReason(), position.getMandateReason()),
                extraction.internalContext() != null ? extraction.internalContext() : position.getInternalContext(),
                extraction.narrative() != null ? extraction.narrative() : position.getNarrative(),
                extraction.reportsTo() != null ? extraction.reportsTo() : position.getReportsTo(),
                extraction.directReports() != null ? extraction.directReports() : position.getDirectReports(),
                extraction.teamSize() != null ? extraction.teamSize() : position.getTeamSize(),
                extraction.location() != null ? extraction.location() : position.getLocation(),
                parseOrKeep(EmploymentType.class, extraction.employmentType(), position.getEmploymentType()),
                extraction.salaryMin() != null ? extraction.salaryMin() : position.getSalaryMin(),
                extraction.salaryMax() != null ? extraction.salaryMax() : position.getSalaryMax(),
                extraction.currency() != null ? extraction.currency() : position.getCurrency(),
                extraction.noticeValue() != null ? extraction.noticeValue() : position.getNoticeValue(),
                parseOrKeep(NoticeUnit.class, extraction.noticeUnit(), position.getNoticeUnit()),
                extraction.bonusTargetPct() != null ? extraction.bonusTargetPct() : position.getBonusTargetPct(),
                extraction.ltip() != null ? extraction.ltip() : position.getLtip(),
                extraction.benefits().isEmpty() ? position.getBenefits() : extraction.benefits(),
                position.isConfidential()));

        if (!extraction.criteria().isEmpty()) {
            // Replaces only what a prior brief (template or document) seeded — a criterion the user
            // typed themselves (fromBrief = false) survives a re-upload untouched.
            List<PositionCriterion> keptUserCriteria = position.getCriteria().stream()
                    .filter(criterion -> !criterion.isFromBrief())
                    .toList();
            List<PositionCriterion> extractedCriteria = extraction.criteria().stream()
                    .map(c -> PositionCriterion.of(c.text(),
                            parseOrDefault(CriterionMode.class, c.mode(), CriterionMode.REQUIRED), true))
                    .toList();
            position.replaceCriteria(Stream.concat(keptUserCriteria.stream(), extractedCriteria.stream())
                    .toList());
        }
        if (!extraction.technical().isEmpty() || !extraction.behavioural().isEmpty()) {
            position.replaceCompetencies(Stream.concat(
                            extraction.technical().stream()
                                    .map(c -> CompetencyRow.of(CompetencyPanel.TECHNICAL, c.name(), c.weight())),
                            extraction.behavioural().stream()
                                    .map(c -> CompetencyRow.of(CompetencyPanel.BEHAVIOURAL, c.name(), c.weight())))
                    .toList());
        }
    }

    private static <E extends Enum<E>> E parseOrKeep(Class<E> type, String raw, E current) {
        return raw == null ? current : parseOrDefault(type, raw, current);
    }

    /** The LLM's schema constrains {@code raw} to valid enum names, but a defensive parse costs little. */
    private static <E extends Enum<E>> E parseOrDefault(Class<E> type, String raw, E fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /**
     * {@code updatedAt}, not {@code createdAt}: a re-upload updates the same row (see
     * {@link PositionDocument#replaceWith}), and the frontend keys its resync-on-extraction remount off
     * this timestamp changing on every successful upload, including a replace of the same file.
     */
    private static BriefDocumentDto documentDto(PositionDocument document) {
        return new BriefDocumentDto(document.getFileName(), document.getContentType(),
                document.getFileSize(), document.getUpdatedAt(), document.getExtractionStatus());
    }

    /**
     * Seeds the brief from the template matched on the project's position title, prefilled with the
     * client's HQ country as the location. Called by {@code ProjectService.create} and by the lazy
     * path in {@link #get} for pre-V7 projects.
     */
    @Transactional
    public Position seedFor(Project project) {
        TemplateSeed seed = PositionTemplates.forTitle(project.getPositionTitle());
        String location = clients.findByIdAndWorkspaceId(project.getClientId(), project.getWorkspaceId())
                .map(client -> client.getHqCountry())
                .orElse(null);

        Position position = Position.forProject(project.getId());
        position.apply(new PositionDetails(
                position.getMandateReason(), null, seed.narrative(),
                seed.reportsTo(), null, null,
                location, EmploymentType.FULL_TIME_PERMANENT,
                null, null, "USD",
                null, null, null, null,
                List.of(), false));
        position.replaceCriteria(seed.criteria());
        position.replaceCompetencies(seed.competencies());
        return positions.save(position);
    }

    /** The lock gate — the only place totals are enforced, so autosave stays permissive. */
    private void requireReady(Position position) {
        boolean technicalBalanced = panelTotal(position, CompetencyPanel.TECHNICAL) == 100;
        boolean behaviouralBalanced = panelTotal(position, CompetencyPanel.BEHAVIOURAL) == 100;
        boolean hasRequired = position.getCriteria().stream()
                .anyMatch(criterion -> criterion.getMode() == CriterionMode.REQUIRED);
        if (!technicalBalanced || !behaviouralBalanced || !hasRequired) {
            throw ApiException.of(ErrorCode.POSITION_NOT_READY);
        }
    }

    private static int panelTotal(Position position, CompetencyPanel panel) {
        return position.getCompetencies().stream()
                .filter(row -> row.getPanel() == panel)
                .mapToInt(CompetencyRow::getWeight)
                .sum();
    }

    private Brief loadUnlockedBrief(UUID projectId, UUID workspaceId) {
        Brief brief = loadBrief(projectId, workspaceId);
        if (brief.position().isLocked()) {
            throw ApiException.of(ErrorCode.POSITION_LOCKED);
        }
        return brief;
    }

    /** The project and its brief together — the target date lives on the project, so both are loaded. */
    private Brief loadBrief(UUID projectId, UUID workspaceId) {
        Project project = requireProject(projectId, workspaceId);
        Position position = positions.findByProjectId(project.getId())
                .orElseGet(() -> seedFor(project));
        return new Brief(project, position);
    }

    private Project requireProject(UUID projectId, UUID workspaceId) {
        return projects.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private record Brief(Project project, Position position) {
    }

    private void auditPositionChange(UUID userId, UUID workspaceId, UUID projectId,
                                     String section, HttpServletRequest httpRequest) {
        audit.event(ProjectEventType.POSITION_UPDATED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("section", section)
                .record();
    }

    private PositionResponse toResponse(Brief brief) {
        Position position = brief.position();
        return new PositionResponse(
                position.getMandateReason(), position.getInternalContext(), position.getNarrative(),
                position.getReportsTo(), position.getDirectReports(), position.getTeamSize(),
                position.getLocation(), position.getEmploymentType(), brief.project().getTargetDate(),
                position.getSalaryMin(), position.getSalaryMax(), position.getCurrency(),
                position.getNoticeValue(), position.getNoticeUnit(), position.getBonusTargetPct(),
                position.getLtip(),
                List.copyOf(position.getBenefits()),
                position.isConfidential(),
                position.getCriteria().stream()
                        .map(c -> new CriterionResponse(c.getText(), c.getMode(), c.isFromBrief()))
                        .toList(),
                panelDtos(position, CompetencyPanel.TECHNICAL),
                panelDtos(position, CompetencyPanel.BEHAVIOURAL),
                position.isLocked(), position.getLockedAt(),
                documents.findByPositionId(position.getId()).map(PositionService::documentDto).orElse(null));
    }

    private static List<CompetencyDto> panelDtos(Position position, CompetencyPanel panel) {
        return position.getCompetencies().stream()
                .filter(row -> row.getPanel() == panel)
                .map(row -> new CompetencyDto(row.getName(), row.getWeight()))
                .toList();
    }
}
