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
import app.lightmove.api.position.model.ProposedPositionDetails;
import app.lightmove.api.position.repository.PositionDocumentRepository;
import jakarta.servlet.http.HttpServletRequest;
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
    private final PositionDetailsProposer proposer;
    private final AuditService audit;
    private final PositionExtractionSettings settings;

    // Hand-written rather than @RequiredArgsConstructor: it derives the settings branch from the
    // properties root rather than taking it, which is the one case the Lombok rule exempts.
    public PositionExtractionService(PositionBriefLoader briefs, PositionDocumentRepository documents,
                                     PositionDocumentTextReader textReader, PositionDetailsProposer proposer,
                                     AuditService audit, LightMoveProperties properties) {
        this.briefs = briefs;
        this.documents = documents;
        this.textReader = textReader;
        this.proposer = proposer;
        this.audit = audit;
        this.settings = properties.position().extraction();
    }

    @Transactional(readOnly = true)
    public PositionExtractionResponse extractDetails(UUID userId, UUID workspaceId, UUID projectId,
                                                      HttpServletRequest httpRequest) {
        if (!settings.enabled()) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "Reading a position description is turned off for this deployment");
        }
        PositionBrief brief = briefs.require(workspaceId, projectId);
        PositionDocument document = documents.findByPositionId(brief.position().getId())
                .orElseThrow(() -> ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                        "Attach a position description before reading it"));

        String text = textReader.read(document.getContent());
        ProposedPositionDetails proposed = proposer.propose(
                userId, text, brief.project().getClientId(), workspaceId);

        audit.event(ProjectEventType.POSITION_DOCUMENT_EXTRACTED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("extractionSource", proposed.source().value())
                .record();

        return assemble(proposed);
    }

    private static PositionExtractionResponse assemble(ProposedPositionDetails proposed) {
        return new PositionExtractionResponse(proposed.source().value(),
                proposed.fields().stream().map(PositionExtractionService::toDto).toList());
    }

    private static ProposedFieldDto toDto(ExtractedField field) {
        return new ProposedFieldDto(field.fieldKey(), field.value(), field.confidence().value(), field.snippet());
    }
}
