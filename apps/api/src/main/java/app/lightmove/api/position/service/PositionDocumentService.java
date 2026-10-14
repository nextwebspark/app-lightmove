package app.lightmove.api.position.service;

import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.PositionDocumentSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.storage.constant.DocumentFormat;
import app.lightmove.api.core.text.service.FileNameSanitizer;
import app.lightmove.api.position.dto.PositionResponse;
import app.lightmove.api.position.model.PositionDocument;
import app.lightmove.api.position.model.StoredDocument;
import app.lightmove.api.position.repository.PositionDocumentRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * The position description attached to a brief: store it, replace it, take it away, hand it back.
 * It never opens the file; only {@link PositionExtractionService}'s explicit call reads one.
 */
@Service
public class PositionDocumentService {

    private final PositionBriefLoader briefs;
    private final PositionResponseAssembler assembler;
    private final PositionDocumentRepository documents;
    private final AuditService audit;
    private final PositionDocumentSettings settings;

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
        String fileName = FileNameSanitizer.sanitize(file.getOriginalFilename(), "position-description");
        String contentType = requireAllowedFormat(fileName, content);

        documents.findByPositionId(brief.position().getId())
                .ifPresentOrElse(
                        existing -> existing.replaceWith(fileName, contentType, content),
                        () -> documents.save(PositionDocument.of(
                                brief.position().getId(), fileName, contentType, content, userId)));

        audit.projectEvent(ProjectEventType.POSITION_DOCUMENT_ATTACHED, userId, workspaceId, projectId, httpRequest)
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
            audit.projectEvent(ProjectEventType.POSITION_DOCUMENT_REMOVED, userId, workspaceId, projectId, httpRequest)
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
     * The format the name and the bytes agree on, never the declared type: that is the sender's claim, and
     * browsers send none, {@code application/octet-stream} or {@code text/plain;charset=UTF-8} for one file.
     */
    private String requireAllowedFormat(String fileName, byte[] content) {
        byte[] header = Arrays.copyOf(content, Math.min(content.length, DocumentFormat.HEADER_LENGTH));
        return DocumentFormat.detect(fileName, header)
                .map(DocumentFormat::contentType)
                .filter(settings::allows)
                .orElseThrow(() -> new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                        "rejected " + DocumentFormat.extensionOf(fileName) + " whose bytes are not an offered format"));
    }
}
