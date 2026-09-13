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
import app.lightmove.api.position.model.ProposedPositionDetails;
import app.lightmove.api.position.service.ExtractionDocumentLoader.LoadedDocument;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Reads the document already attached to a mandate's brief into a step-one proposal — the one
 * explicit act that opens {@link PositionDocumentService}'s "never read" boundary, on its own call,
 * never as a side effect of upload.
 *
 * <p>Its own class rather than more methods on {@link PositionDocumentService} or {@link
 * PositionService}, for the same reason the document service is already split out: this orchestrates
 * a byte-reading, a redaction and a billed model call, none of which belong inside {@link
 * PositionDocumentService#attach}'s write transaction. This method is read-only — nothing here writes
 * a row, and nothing should ever make it. It is also deliberately not {@code @Transactional} itself:
 * {@link ExtractionDocumentLoader} holds the one short transaction this needs, so the PDF parse and
 * the Vertex round trip below never pin a database connection.
 */
@Service
public class PositionExtractionService {

    private final ExtractionDocumentLoader documentLoader;
    private final PositionDocumentTextReader textReader;
    private final PositionDetailsProposer proposer;
    private final AuditService audit;
    private final PositionExtractionSettings settings;

    // Hand-written rather than @RequiredArgsConstructor: it derives the settings branch from the
    // properties root rather than taking it, which is the one case the Lombok rule exempts.
    public PositionExtractionService(ExtractionDocumentLoader documentLoader,
                                     PositionDocumentTextReader textReader, PositionDetailsProposer proposer,
                                     AuditService audit, LightMoveProperties properties) {
        this.documentLoader = documentLoader;
        this.textReader = textReader;
        this.proposer = proposer;
        this.audit = audit;
        this.settings = properties.position().extraction();
    }

    public PositionExtractionResponse extractDetails(UUID userId, UUID workspaceId, UUID projectId,
                                                      HttpServletRequest httpRequest) {
        if (!settings.enabled()) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "Reading a position description is turned off for this deployment");
        }
        LoadedDocument document = documentLoader.require(workspaceId, projectId);

        String text = textReader.read(document.content());
        ProposedPositionDetails proposed = proposer.propose(
                userId, text, document.clientId(), workspaceId);

        audit.event(ProjectEventType.POSITION_DOCUMENT_EXTRACTED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("extractionSource", proposed.source().value())
                .record();

        return assemble(proposed);
    }

    private static PositionExtractionResponse assemble(ProposedPositionDetails proposed) {
        List<ExtractedField> fields = proposed.fields();
        List<ProposedFieldDto> dtos = new ArrayList<>(fields.size());
        for (int i = 0; i < fields.size(); i++) {
            dtos.add(toDto(i, fields.get(i)));
        }
        return new PositionExtractionResponse(proposed.source().value(), dtos);
    }

    private static ProposedFieldDto toDto(int id, ExtractedField field) {
        return new ProposedFieldDto(id, field.fieldKey(), field.value(), field.confidence().value(),
                field.snippet());
    }
}
