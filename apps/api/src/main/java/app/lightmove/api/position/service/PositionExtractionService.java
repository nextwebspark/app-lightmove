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
import app.lightmove.api.position.model.ProposedCompensation;
import app.lightmove.api.position.model.ProposedMandateContext;
import app.lightmove.api.position.model.ProposedPositionDetails;
import app.lightmove.api.position.model.ProposedReportingStructure;
import app.lightmove.api.position.service.ExtractionDocumentLoader.LoadedDocument;
import app.lightmove.api.positiontemplate.dto.PositionTemplateSummary;
import app.lightmove.api.positiontemplate.service.PositionTemplateService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Reads the document already attached to a mandate's brief into a step proposal — the one explicit
 * act that opens {@link PositionDocumentService}'s "never read" boundary, on its own call, never as a
 * side effect of upload.
 *
 * <p>Its own class rather than more methods on {@link PositionDocumentService} or {@link
 * PositionService}, for the same reason the document service is already split out: this orchestrates
 * a byte-reading, a redaction and a billed model call, none of which belong inside {@link
 * PositionDocumentService#attach}'s write transaction. Every method here is read-only — nothing here
 * writes a row, and nothing should ever make it. None of them are {@code @Transactional} themselves:
 * {@link ExtractionDocumentLoader} holds the one short transaction each needs, so the PDF parse and
 * the Vertex round trip below never pin a database connection.
 */
@Service
public class PositionExtractionService {

    private final ExtractionDocumentLoader documentLoader;
    private final PositionDocumentTextReader textReader;
    private final PositionDetailsProposer detailsProposer;
    private final PositionContextProposer contextProposer;
    private final PositionCompensationProposer compensationProposer;
    private final PositionAssessmentProposer assessmentProposer;
    private final PositionReportingProposer reportingProposer;
    private final PositionTemplateService templates;
    private final AuditService audit;
    private final PositionExtractionSettings settings;

    // Hand-written rather than @RequiredArgsConstructor: it derives the settings branch from the
    // properties root rather than taking it, which is the one case the Lombok rule exempts.
    public PositionExtractionService(ExtractionDocumentLoader documentLoader,
                                     PositionDocumentTextReader textReader,
                                     PositionDetailsProposer detailsProposer,
                                     PositionContextProposer contextProposer,
                                     PositionCompensationProposer compensationProposer,
                                     PositionAssessmentProposer assessmentProposer,
                                     PositionReportingProposer reportingProposer,
                                     PositionTemplateService templates,
                                     AuditService audit, LightMoveProperties properties) {
        this.documentLoader = documentLoader;
        this.textReader = textReader;
        this.detailsProposer = detailsProposer;
        this.contextProposer = contextProposer;
        this.compensationProposer = compensationProposer;
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
        return assemble(proposed.source().value(), proposed.fields(), suggestedTemplate);
    }

    public PositionExtractionResponse extractContext(UUID userId, UUID workspaceId, UUID projectId,
                                                      HttpServletRequest httpRequest) {
        LoadedDocument document = load(workspaceId, projectId);
        String text = textReader.read(document.content());
        ProposedMandateContext proposed = contextProposer.propose(
                userId, text, document.clientId(), workspaceId, document.roleTitle());
        recordAudit(userId, workspaceId, projectId, httpRequest, proposed.source().value());
        return assemble(proposed.source().value(), proposed.fields(), null);
    }

    public PositionExtractionResponse extractCompensation(UUID userId, UUID workspaceId, UUID projectId,
                                                           HttpServletRequest httpRequest) {
        LoadedDocument document = load(workspaceId, projectId);
        String text = textReader.read(document.content());
        ProposedCompensation proposed = compensationProposer.propose(
                userId, text, document.clientId(), workspaceId, document.roleTitle());
        recordAudit(userId, workspaceId, projectId, httpRequest, proposed.source().value());
        return assemble(proposed.source().value(), proposed.fields(), null);
    }

    public PositionExtractionResponse extractAssessment(UUID userId, UUID workspaceId, UUID projectId,
                                                         HttpServletRequest httpRequest) {
        LoadedDocument document = load(workspaceId, projectId);
        String text = textReader.read(document.content());
        ProposedAssessment proposed = assessmentProposer.propose(
                userId, text, document.clientId(), workspaceId, document.roleTitle());
        recordAudit(userId, workspaceId, projectId, httpRequest, proposed.source().value());
        return assemble(proposed.source().value(), proposed.fields(), null);
    }

    public PositionExtractionResponse extractReporting(UUID userId, UUID workspaceId, UUID projectId,
                                                        HttpServletRequest httpRequest) {
        LoadedDocument document = load(workspaceId, projectId);
        String text = textReader.read(document.content());
        ProposedReportingStructure proposed = reportingProposer.propose(
                userId, text, document.clientId(), workspaceId, document.roleTitle());
        recordAudit(userId, workspaceId, projectId, httpRequest, proposed.source().value());
        return assemble(proposed.source().value(), proposed.fields(), null);
    }

    /**
     * The brief template the extracted role title matches, offered as a separate whole-brief opt-in
     * — never {@code roleTitle}'s own template-backfill match in {@link PositionDetailsProposer},
     * which falls back to generic-executive for a field it must fill in either way. This suggestion
     * has no such obligation, so it stays silent rather than offering a fallback as though it were
     * a real match.
     */
    private PositionTemplateSummary suggestedTemplateFor(List<ExtractedField> fields, UUID workspaceId) {
        return fields.stream()
                .filter(field -> field.fieldKey().equals("roleTitle"))
                .map(ExtractedField::value)
                .findFirst()
                .flatMap(roleTitle -> templates.suggestFor(workspaceId, roleTitle))
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
        audit.event(ProjectEventType.POSITION_DOCUMENT_EXTRACTED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("extractionSource", extractionSource)
                .record();
    }

    private static PositionExtractionResponse assemble(String extractionSource, List<ExtractedField> fields,
                                                        PositionTemplateSummary suggestedTemplate) {
        List<ProposedFieldDto> dtos = new ArrayList<>(fields.size());
        for (int i = 0; i < fields.size(); i++) {
            dtos.add(toDto(i, fields.get(i)));
        }
        return new PositionExtractionResponse(extractionSource, dtos, suggestedTemplate);
    }

    private static ProposedFieldDto toDto(int id, ExtractedField field) {
        return new ProposedFieldDto(id, field.fieldKey(), field.value(), field.confidence().value(),
                field.snippet(), field.origin().value());
    }
}
