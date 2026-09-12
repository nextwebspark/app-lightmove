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
import app.lightmove.api.position.model.PositionDocument;
import app.lightmove.api.position.model.ProposedCompensation;
import app.lightmove.api.position.model.ProposedMandateContext;
import app.lightmove.api.position.model.ProposedPositionDetails;
import app.lightmove.api.position.repository.PositionDocumentRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the document already attached to a mandate's brief into a step-one proposal — the one
 * explicit act that opens {@link PositionDocumentService}'s "never read" boundary, on its own call,
 * never as a side effect of upload.
 *
 * <p>Its own class rather than more methods on {@link PositionDocumentService} or {@link
 * PositionService}, for the same reason the document service is already split out: this orchestrates
 * a byte-reading, a redaction and a billed model call, none of which belong inside {@link
 * PositionDocumentService#attach}'s write transaction. This method is read-only — nothing here writes
 * a row, and nothing should ever make it.
 */
@Service
public class PositionExtractionService {

    private final PositionBriefLoader briefs;
    private final PositionDocumentRepository documents;
    private final PositionDocumentTextReader textReader;
    private final PositionDetailsProposer detailsProposer;
    private final PositionContextProposer contextProposer;
    private final PositionCompensationProposer compensationProposer;
    private final AuditService audit;
    private final PositionExtractionSettings settings;

    // Hand-written rather than @RequiredArgsConstructor: it derives the settings branch from the
    // properties root rather than taking it, which is the one case the Lombok rule exempts.
    public PositionExtractionService(PositionBriefLoader briefs, PositionDocumentRepository documents,
                                     PositionDocumentTextReader textReader, PositionDetailsProposer detailsProposer,
                                     PositionContextProposer contextProposer,
                                     PositionCompensationProposer compensationProposer,
                                     AuditService audit, LightMoveProperties properties) {
        this.briefs = briefs;
        this.documents = documents;
        this.textReader = textReader;
        this.detailsProposer = detailsProposer;
        this.contextProposer = contextProposer;
        this.compensationProposer = compensationProposer;
        this.audit = audit;
        this.settings = properties.position().extraction();
    }

    @Transactional(readOnly = true)
    public PositionExtractionResponse extractDetails(UUID userId, UUID workspaceId, UUID projectId,
                                                      HttpServletRequest httpRequest) {
        Read read = load(workspaceId, projectId);
        ProposedPositionDetails proposed = detailsProposer.propose(
                userId, read.text(), read.brief().project().getClientId(), workspaceId);
        recordAudit(userId, workspaceId, projectId, httpRequest, proposed.source().value());
        return assemble(proposed.source().value(), proposed.fields());
    }

    @Transactional(readOnly = true)
    public PositionExtractionResponse extractContext(UUID userId, UUID workspaceId, UUID projectId,
                                                      HttpServletRequest httpRequest) {
        Read read = load(workspaceId, projectId);
        ProposedMandateContext proposed = contextProposer.propose(
                userId, read.text(), read.brief().project().getClientId(), workspaceId);
        recordAudit(userId, workspaceId, projectId, httpRequest, proposed.source().value());
        return assemble(proposed.source().value(), proposed.fields());
    }

    @Transactional(readOnly = true)
    public PositionExtractionResponse extractCompensation(UUID userId, UUID workspaceId, UUID projectId,
                                                           HttpServletRequest httpRequest) {
        Read read = load(workspaceId, projectId);
        ProposedCompensation proposed = compensationProposer.propose(
                userId, read.text(), read.brief().project().getClientId(), workspaceId);
        recordAudit(userId, workspaceId, projectId, httpRequest, proposed.source().value());
        return assemble(proposed.source().value(), proposed.fields());
    }

    private record Read(PositionBrief brief, String text) {}

    private Read load(UUID workspaceId, UUID projectId) {
        if (!settings.enabled()) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "Reading a position description is turned off for this deployment");
        }
        PositionBrief brief = briefs.require(workspaceId, projectId);
        PositionDocument document = documents.findByPositionId(brief.position().getId())
                .orElseThrow(() -> ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                        "Attach a position description before reading it"));
        return new Read(brief, textReader.read(document.getContent()));
    }

    private void recordAudit(UUID userId, UUID workspaceId, UUID projectId, HttpServletRequest httpRequest,
                             String extractionSource) {
        audit.event(ProjectEventType.POSITION_DOCUMENT_EXTRACTED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("extractionSource", extractionSource)
                .record();
    }

    private static PositionExtractionResponse assemble(String extractionSource, List<ExtractedField> fields) {
        return new PositionExtractionResponse(extractionSource,
                fields.stream().map(PositionExtractionService::toDto).toList());
    }

    private static ProposedFieldDto toDto(ExtractedField field) {
        return new ProposedFieldDto(field.fieldKey(), field.value(), field.confidence().value(), field.snippet());
    }
}
