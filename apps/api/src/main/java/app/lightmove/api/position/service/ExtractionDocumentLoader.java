package app.lightmove.api.position.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.position.model.PositionDocument;
import app.lightmove.api.position.repository.PositionDocumentRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reads every {@code PositionExtractionService} extraction method needs before it can start
 * parsing and calling the model — its own bean, and its own short transaction, so that transaction
 * ends before the PDF parse and the Vertex round trip begin. Kept separate from {@link
 * PositionExtractionService} rather than a private method on it: Spring's proxy-based {@code
 * @Transactional} does not apply to self-invocation, so a transactional boundary that short has to
 * live on a different bean to mean anything.
 */
@Component
@RequiredArgsConstructor
class ExtractionDocumentLoader {

    private final PositionBriefLoader briefs;
    private final PositionDocumentRepository documents;

    /** {@code roleTitle} is the mandate's persisted title — the match key step five's controlled
     * vocabulary and step three's usual-direct-reports both need, since neither extracts a title of
     * its own the way step one does. */
    record LoadedDocument(byte[] content, UUID clientId, String roleTitle) {}

    @Transactional(readOnly = true)
    LoadedDocument require(UUID workspaceId, UUID projectId) {
        PositionBrief brief = briefs.require(workspaceId, projectId);
        PositionDocument document = documents.findByPositionId(brief.position().getId())
                .orElseThrow(() -> ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                        "Attach a position description before reading it"));
        return new LoadedDocument(document.getContent(), brief.project().getClientId(),
                brief.project().getPositionTitle());
    }
}
