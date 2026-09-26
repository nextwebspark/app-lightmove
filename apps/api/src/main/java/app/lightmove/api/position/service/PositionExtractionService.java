package app.lightmove.api.position.service;

import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.PositionExtractionSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.position.dto.PositionExtractionResponse;
import app.lightmove.api.position.dto.ProposedFieldDto;
import app.lightmove.api.position.model.ExtractedField;
import app.lightmove.api.position.model.ProposedAssessment;
import app.lightmove.api.position.model.ProposedMandateContext;
import app.lightmove.api.position.model.ProposedPositionDetails;
import app.lightmove.api.position.model.ProposedReportingStructure;
import app.lightmove.api.position.service.ExtractionDocumentLoader.LoadedDocument;
import app.lightmove.api.positiontemplate.dto.PositionTemplateSummary;
import app.lightmove.api.positiontemplate.service.PositionTemplateService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Reads the document attached to a mandate's brief into a step proposal — the one explicit act that
 * opens {@link PositionDocumentService}'s "never read" boundary. Compensation is never read.
 *
 * <p>Writes no row, and is deliberately not {@code @Transactional}: {@link ExtractionDocumentLoader}
 * holds the one short transaction, so the parse and the model round trip never pin a connection.
 */
@Service
public class PositionExtractionService {

    private final ExtractionDocumentLoader documentLoader;
    private final PositionDocumentTextReader textReader;
    private final PositionDetailsProposer detailsProposer;
    private final PositionContextProposer contextProposer;
    private final PositionAssessmentProposer assessmentProposer;
    private final PositionReportingProposer reportingProposer;
    private final PositionTemplateService templates;
    private final AuditService audit;
    private final PositionExtractionSettings settings;

    public PositionExtractionService(ExtractionDocumentLoader documentLoader,
                                     PositionDocumentTextReader textReader,
                                     PositionDetailsProposer detailsProposer,
                                     PositionContextProposer contextProposer,
                                     PositionAssessmentProposer assessmentProposer,
                                     PositionReportingProposer reportingProposer,
                                     PositionTemplateService templates,
                                     AuditService audit, LightMoveProperties properties) {
        this.documentLoader = documentLoader;
        this.textReader = textReader;
        this.detailsProposer = detailsProposer;
        this.contextProposer = contextProposer;
        this.assessmentProposer = assessmentProposer;
        this.reportingProposer = reportingProposer;
        this.templates = templates;
        this.audit = audit;
        this.settings = properties.position().extraction();
    }

    public PositionExtractionResponse extractDetails(UUID userId, UUID workspaceId, UUID projectId,
                                                      HttpServletRequest httpRequest) {
        LoadedDocument document = load(workspaceId, projectId);
        String text = textReader.read(document.content());
        ProposedPositionDetails proposed = detailsProposer.propose(
                userId, text, document.clientId(), workspaceId);
        recordAudit(userId, workspaceId, projectId, httpRequest, proposed.source().value());
        PositionTemplateSummary suggestedTemplate = suggestedTemplateFor(proposed.fields(), workspaceId);
        return assemble(proposed.source().value(), proposed.fields(), suggestedTemplate, null);
    }

    public PositionExtractionResponse extractContext(UUID userId, UUID workspaceId, UUID projectId,
                                                      HttpServletRequest httpRequest) {
        LoadedDocument document = load(workspaceId, projectId);
        String text = textReader.read(document.content());
        ProposedMandateContext proposed = contextProposer.propose(
                userId, text, document.clientId(), workspaceId);
        recordAudit(userId, workspaceId, projectId, httpRequest, proposed.source().value());
        return assemble(proposed.source().value(), proposed.fields(), null, null);
    }

    public PositionExtractionResponse extractAssessment(UUID userId, UUID workspaceId, UUID projectId,
                                                         HttpServletRequest httpRequest) {
        LoadedDocument document = load(workspaceId, projectId);
        String text = textReader.read(document.content());
        ProposedAssessment proposed = assessmentProposer.propose(
                userId, text, document.clientId(), workspaceId, document.roleTitle());
        recordAudit(userId, workspaceId, projectId, httpRequest, proposed.source().value());
        return assemble(proposed.source().value(), proposed.fields(), null, null);
    }

    public PositionExtractionResponse extractReporting(UUID userId, UUID workspaceId, UUID projectId,
                                                        HttpServletRequest httpRequest) {
        LoadedDocument document = load(workspaceId, projectId);
        String text = textReader.read(document.content());
        ProposedReportingStructure proposed = reportingProposer.propose(
                userId, text, document.clientId(), workspaceId);
        recordAudit(userId, workspaceId, projectId, httpRequest, proposed.source().value());
        List<String> usualDirectReports = usualDirectReportsFor(workspaceId, document.roleTitle());
        return assemble(proposed.source().value(), proposed.fields(), null, usualDirectReports);
    }

    /** Never the generic fallback: an unmatched title suggests nothing. */
    private PositionTemplateSummary suggestedTemplateFor(List<ExtractedField> fields, UUID workspaceId) {
        return fields.stream()
                .filter(field -> field.fieldKey().equals("roleTitle"))
                .map(ExtractedField::value)
                .findFirst()
                .flatMap(roleTitle -> templates.suggestFor(workspaceId, roleTitle))
                .orElse(null);
    }

    /**
     * For the reporting step's Suggested seats (#398), never from the generic fallback. De-duplicated,
     * since a template's list is workspace-writable text, not a verified reading.
     */
    private List<String> usualDirectReportsFor(UUID workspaceId, String roleTitle) {
        if (roleTitle == null || roleTitle.isBlank()) {
            return null;
        }
        return templates.matchingByTitle(workspaceId, roleTitle)
                .map(template -> {
                    Set<String> seenCaseInsensitive = new LinkedHashSet<>();
                    List<String> reports = new ArrayList<>();
                    for (String title : template.getBody().directReports()) {
                        if (title == null || title.isBlank()) {
                            continue;
                        }
                        String trimmed = title.trim();
                        if (seenCaseInsensitive.add(trimmed.toLowerCase(Locale.ROOT))) {
                            reports.add(trimmed);
                        }
                    }
                    return reports;
                })
                .orElse(null);
    }

    private LoadedDocument load(UUID workspaceId, UUID projectId) {
        if (!settings.enabled()) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "Reading a position description is turned off for this deployment");
        }
        return documentLoader.require(workspaceId, projectId);
    }

    private void recordAudit(UUID userId, UUID workspaceId, UUID projectId, HttpServletRequest httpRequest,
                             String extractionSource) {
        audit.projectEvent(ProjectEventType.POSITION_DOCUMENT_EXTRACTED, userId, workspaceId, projectId, httpRequest)
                .detail("extractionSource", extractionSource)
                .record();
    }

    private static PositionExtractionResponse assemble(String extractionSource, List<ExtractedField> fields,
                                                        PositionTemplateSummary suggestedTemplate,
                                                        List<String> usualDirectReports) {
        List<ProposedFieldDto> dtos = new ArrayList<>(fields.size());
        for (int i = 0; i < fields.size(); i++) {
            dtos.add(toDto(i, fields.get(i)));
        }
        return new PositionExtractionResponse(extractionSource, dtos, suggestedTemplate, usualDirectReports);
    }

    private static ProposedFieldDto toDto(int id, ExtractedField field) {
        return new ProposedFieldDto(id, field.fieldKey(), field.value(), field.confidence().value(),
                field.snippet(), field.origin().value());
    }
}
