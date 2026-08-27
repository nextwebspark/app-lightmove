package app.lightmove.api.position.service;

import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.PositionDocumentSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.position.dto.PositionResponse;
import app.lightmove.api.position.model.PositionDocument;
import app.lightmove.api.position.model.StoredDocument;
import app.lightmove.api.position.repository.PositionDocumentRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * The position description attached to a brief: store it, replace it, take it away, hand it back.
 *
 * <p><b>It is never read.</b> Nothing here opens the file or fills a field in from it — the mandate
 * keeps the document it was briefed from, and every field on the screen is typed by hand. An
 * extraction that pre-fills the brief is a separate feature that does not exist yet, and this service
 * deliberately does not pretend otherwise.
 *
 * <p>Its own class rather than more methods on {@link PositionService}: bytes, size ceilings and
 * content-type policy change for different reasons than the brief's fields do, and this is the one
 * class a move to object storage would touch.
 */
@Service
public class PositionDocumentService {

    private final PositionBriefLoader briefs;
    private final PositionResponseAssembler assembler;
    private final PositionDocumentRepository documents;
    private final AuditService audit;
    private final PositionDocumentSettings settings;

    // Hand-written rather than @RequiredArgsConstructor: it derives the settings branch from the
    // properties root rather than taking it, which is the one case the Lombok rule exempts.
    public PositionDocumentService(PositionBriefLoader briefs,
                                   PositionResponseAssembler assembler,
                                   PositionDocumentRepository documents,
                                   AuditService audit,
                                   LightMoveProperties properties) {
        this.briefs = briefs;
        this.assembler = assembler;
        this.documents = documents;
        this.audit = audit;
        this.settings = properties.position().document();
    }

    @Transactional
    public PositionResponse attach(UUID userId, UUID workspaceId, UUID projectId,
                                   MultipartFile file, HttpServletRequest httpRequest) {
        PositionBrief brief = briefs.require(workspaceId, projectId);
        byte[] content = contentOf(file);
        String contentType = requireAllowedType(file.getContentType());
        String fileName = safeFileNameOf(file.getOriginalFilename());

        // One document per position: replacing keeps the row rather than accumulating versions.
        documents.findByPositionId(brief.position().getId())
                .ifPresentOrElse(
                        existing -> existing.replaceWith(fileName, contentType, content),
                        () -> documents.save(PositionDocument.of(
                                brief.position().getId(), fileName, contentType, content, userId)));

        audit.event(ProjectEventType.POSITION_DOCUMENT_ATTACHED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("fileName", fileName)
                .record();
        return assembler.assemble(brief);
    }

    @Transactional
    public PositionResponse remove(UUID userId, UUID workspaceId, UUID projectId,
                                   HttpServletRequest httpRequest) {
        PositionBrief brief = briefs.require(workspaceId, projectId);
        documents.findByPositionId(brief.position().getId()).ifPresent(document -> {
            documents.delete(document);
            audit.event(ProjectEventType.POSITION_DOCUMENT_REMOVED)
                    .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                    .detail("fileName", document.getFileName())
                    .record();
        });
        return assembler.assemble(brief);
    }

    @Transactional(readOnly = true)
    public StoredDocument download(UUID workspaceId, UUID projectId) {
        PositionBrief brief = briefs.require(workspaceId, projectId);
        return documents.findByPositionId(brief.position().getId())
                .map(document -> new StoredDocument(
                        document.getFileName(), document.getContentType(), document.getContent()))
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private byte[] contentOf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED, "Choose a file to attach");
        }
        if (file.getSize() > settings.maxFileSizeBytes()) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE,
                    "upload of " + file.getSize() + " bytes exceeds " + settings.maxFileSizeBytes());
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded position description", e);
        }
    }

    /**
     * The declared content type is a claim made by whatever sent the request, so the allowlist decides
     * and an unrecognised type is refused rather than stored and echoed back at download time.
     */
    private String requireAllowedType(String declaredContentType) {
        if (!settings.allows(declaredContentType)) {
            throw new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "rejected content type " + declaredContentType);
        }
        return declaredContentType;
    }

    /**
     * The original filename is caller-supplied and reaches a {@code Content-Disposition} header on the
     * way back out, so the path separators and control characters that would let it forge a header or
     * name a directory are stripped here rather than at every reader.
     */
    private static String safeFileNameOf(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "position-description";
        }
        String withoutPath = originalFileName.replaceAll(".*[/\\\\]", "");
        String cleaned = withoutPath.replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (cleaned.isEmpty()) {
            return "position-description";
        }
        return cleaned.length() > 255 ? cleaned.substring(0, 255) : cleaned;
    }
}
