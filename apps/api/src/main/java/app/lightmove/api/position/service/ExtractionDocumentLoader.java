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
 * The reads an extraction needs, in a short transaction that ends before the parse and the model call.
 * A separate bean because proxy-based {@code @Transactional} does not apply to self-invocation.
 */
@Component
@RequiredArgsConstructor
class ExtractionDocumentLoader {

    private final PositionBriefLoader briefs;
    private final PositionDocumentRepository documents;

    /** {@code roleTitle} is the mandate's persisted title, the template match key for steps three and five. */
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
