package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.PersonDocumentCategory;
import app.lightmove.api.candidate.dto.PersonDocumentResponse;
import app.lightmove.api.candidate.dto.PersonDocumentUploadResponse;
import app.lightmove.api.candidate.dto.PersonDocumentVersionResponse;
import app.lightmove.api.candidate.dto.UpdatePersonDocumentRequest;
import app.lightmove.api.candidate.model.FiledPersonDocument;
import app.lightmove.api.candidate.model.PersonDocument;
import app.lightmove.api.candidate.model.PersonDocumentVersion;
import app.lightmove.api.candidate.model.StagedDocumentFile;
import app.lightmove.api.candidate.model.StoredDocumentFile;
import app.lightmove.api.candidate.repository.PersonDocumentRepository;
import app.lightmove.api.candidate.repository.PersonDocumentVersionRepository;
import app.lightmove.api.candidate.repository.PersonRepository;
import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.PersonDocumentSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.storage.constant.DocumentFormat;
import app.lightmove.api.core.storage.service.DocumentStore;
import app.lightmove.api.core.text.service.FileNameSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * A person's documents — CV, cover letter, references — shared by every mandate that maps them and read
 * by staff only (decision D1). An upload is checked by its bytes, hashed and written to storage before
 * any row exists, then filed by {@link PersonDocumentWriter}; a filing that fails takes its file back
 * out, and a removal deletes its files only once the rows are gone, so a crash between the two leaves
 * an unreferenced object, never a row pointing at nothing.
 */
@Slf4j
@Service
public class PersonDocumentService {

    private static final String PERSON_TARGET = "person";
    private static final String FALLBACK_FILE_NAME = "document";
    private static final Comparator<PersonDocumentResponse> CARD_ORDER =
            Comparator.comparing(PersonDocumentResponse::primaryCv).reversed()
                    .thenComparing(card -> ApiValueEnum.fromValue(PersonDocumentCategory.class, card.category()))
                    .thenComparing(PersonDocumentService::latestUploadOf, Comparator.reverseOrder());

    private final PersonRepository people;
    private final PersonDocumentRepository documents;
    private final PersonDocumentVersionRepository versions;
    private final PersonDocumentWriter writer;
    private final DocumentStore store;
    private final UserRepository users;
    private final WorkspaceAccess workspaceAccess;
    private final AuditService audit;
    private final PersonDocumentSettings settings;

    public PersonDocumentService(PersonRepository people, PersonDocumentRepository documents,
                                 PersonDocumentVersionRepository versions, PersonDocumentWriter writer,
                                 DocumentStore store, UserRepository users, WorkspaceAccess workspaceAccess,
                                 AuditService audit, LightMoveProperties properties) {
        this.people = people;
        this.documents = documents;
        this.versions = versions;
        this.writer = writer;
        this.store = store;
        this.users = users;
        this.workspaceAccess = workspaceAccess;
        this.audit = audit;
        this.settings = properties.personDocuments();
    }

    @Transactional(readOnly = true)
    public List<PersonDocumentResponse> list(UUID userId, UUID workspaceId, UUID personId) {
        people.requireInWorkspace(personId, workspaceId);
        List<PersonDocument> found = documents.findByWorkspaceIdAndPersonId(workspaceId, personId);
        Map<UUID, List<PersonDocumentVersion>> stacks = versions.findByPersonIdOrderByVersionNoDesc(personId)
                .stream().collect(Collectors.groupingBy(PersonDocumentVersion::getDocumentId));
        Map<UUID, User> named = usersOf(found, stacks.values().stream().flatMap(List::stream).toList());
        boolean mayRemoveAny = mayRemoveAny(userId, workspaceId);
        return found.stream()
                .map(document -> toDto(document, stacks.getOrDefault(document.getId(), List.of()), named, userId,
                        mayRemoveAny))
                .sorted(CARD_ORDER)
                .toList();
    }

    /**
     * {@code projectId} is the position the upload came through, null on the workspace's routes. Unless
     * {@code asNewDocument}, a file named like one already on the person becomes its next version.
     */
    public PersonDocumentUploadResponse upload(UUID userId, UUID workspaceId, UUID personId, UUID projectId,
                                               MultipartFile file, String category, boolean asNewDocument,
                                               HttpServletRequest httpRequest) {
        people.requireInWorkspace(personId, workspaceId);
        PersonDocumentCategory filedAs = ApiValueEnum.parse(PersonDocumentCategory.class, category, null,
                "document category");
        StagedDocumentFile staged = stage(workspaceId, personId, file);
        FiledPersonDocument filed = fileOrDiscard(staged,
                () -> writer.file(userId, workspaceId, personId, projectId, staged, filedAs, asNewDocument));
        return recorded(userId, workspaceId, personId, filed, staged, httpRequest);
    }

    public PersonDocumentUploadResponse uploadVersion(UUID userId, UUID workspaceId, UUID personId,
                                                      UUID documentId, UUID projectId, MultipartFile file,
                                                      HttpServletRequest httpRequest) {
        documents.requireOnPerson(documentId, workspaceId, personId);
        StagedDocumentFile staged = stage(workspaceId, personId, file);
        FiledPersonDocument filed = fileOrDiscard(staged,
                () -> writer.addVersion(userId, workspaceId, personId, documentId, projectId, staged));
        return recorded(userId, workspaceId, personId, filed, staged, httpRequest);
    }

    /** Renaming, recategorising and the CV mark are anyone's who reaches the person, and no timeline line. */
    @Transactional
    public PersonDocumentResponse update(UUID userId, UUID workspaceId, UUID personId, UUID documentId,
                                         UpdatePersonDocumentRequest request, HttpServletRequest httpRequest) {
        people.requireInWorkspace(personId, workspaceId);
        PersonDocumentCategory category = ApiValueEnum.parse(PersonDocumentCategory.class, request.category(),
                null, "document category");
        PersonDocument document = writer.update(workspaceId, personId, documentId, request.title(), category,
                request.primaryCv());
        audit.event(ProjectEventType.PERSON_DOCUMENT_UPDATED).actor(userId).workspace(workspaceId)
                .target(PERSON_TARGET, personId).from(httpRequest)
                .detail("documentId", documentId.toString())
                .record();
        return cardOf(userId, workspaceId, document);
    }

    public void remove(UUID userId, UUID workspaceId, UUID personId, UUID documentId, UUID projectId,
                       HttpServletRequest httpRequest) {
        List<String> keys = writer.remove(userId, workspaceId, personId, documentId, projectId,
                mayRemoveAny(userId, workspaceId));
        keys.forEach(this::deleteQuietly);
        audit.event(ProjectEventType.PERSON_DOCUMENT_REMOVED).actor(userId).workspace(workspaceId)
                .target(PERSON_TARGET, personId).from(httpRequest)
                .detail("documentId", documentId.toString())
                .record();
    }

    public void removeVersion(UUID userId, UUID workspaceId, UUID personId, UUID documentId, UUID versionId,
                              UUID projectId, HttpServletRequest httpRequest) {
        List<String> keys = writer.removeVersion(userId, workspaceId, personId, documentId, versionId, projectId,
                mayRemoveAny(userId, workspaceId));
        keys.forEach(this::deleteQuietly);
        audit.event(ProjectEventType.PERSON_DOCUMENT_VERSION_REMOVED).actor(userId).workspace(workspaceId)
                .target(PERSON_TARGET, personId).from(httpRequest)
                .detail("documentId", documentId.toString())
                .detail("versionId", versionId.toString())
                .record();
    }

    /** The version's file, and an audit event saying it left: a CV downloaded is a CV out of the app. */
    @Transactional(readOnly = true)
    public StoredDocumentFile fileOf(UUID userId, UUID workspaceId, UUID personId, UUID documentId, UUID versionId,
                                     boolean preview, HttpServletRequest httpRequest) {
        people.requireInWorkspace(personId, workspaceId);
        documents.requireOnPerson(documentId, workspaceId, personId);
        PersonDocumentVersion version = versions.findByIdAndDocumentId(versionId, documentId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        audit.event(ProjectEventType.PERSON_DOCUMENT_DOWNLOADED).actor(userId).workspace(workspaceId)
                .target(PERSON_TARGET, personId).from(httpRequest)
                .detail("documentId", documentId.toString())
                .detail("versionId", versionId.toString())
                .detail("preview", Boolean.toString(preview))
                .record();
        return new StoredDocumentFile(version.getFileName(), version.getContentType(), version.getSizeBytes(),
                version.getStorageKey(), isPreviewable(version.getContentType()));
    }

    public InputStream open(StoredDocumentFile file) {
        try {
            return store.open(file.storageKey());
        } catch (IOException failure) {
            throw new UncheckedIOException("could not open a stored person document", failure);
        }
    }

    private PersonDocumentUploadResponse recorded(UUID userId, UUID workspaceId, UUID personId,
                                                  FiledPersonDocument filed, StagedDocumentFile staged,
                                                  HttpServletRequest httpRequest) {
        ProjectEventType event = switch (filed.outcome()) {
            case CREATED -> ProjectEventType.PERSON_DOCUMENT_ADDED;
            case NEW_VERSION -> ProjectEventType.PERSON_DOCUMENT_VERSION_ADDED;
        };
        audit.event(event).actor(userId).workspace(workspaceId)
                .target(PERSON_TARGET, personId).from(httpRequest)
                .detail("documentId", filed.document().getId().toString())
                .detail("fileName", staged.fileName())
                .record();
        return new PersonDocumentUploadResponse(filed.outcome().value(),
                cardOf(userId, workspaceId, documents.requireOnPerson(filed.document().getId(), workspaceId,
                        personId)));
    }

    private FiledPersonDocument fileOrDiscard(StagedDocumentFile staged,
                                              Supplier<FiledPersonDocument> filing) {
        try {
            return filing.get();
        } catch (RuntimeException failure) {
            deleteQuietly(staged.storageKey());
            throw failure;
        }
    }

    /**
     * Read twice from the container's spooled copy rather than held in memory: once for the format, the
     * hash and the duplicate check, and once more into storage only if all three pass.
     */
    private StagedDocumentFile stage(UUID workspaceId, UUID personId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED, "Choose a file to upload");
        }
        if (file.getSize() > settings.maxFileSizeBytes()) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE,
                    "upload of " + file.getSize() + " bytes exceeds " + settings.maxFileSizeBytes());
        }
        String fileName = FileNameSanitizer.sanitize(file.getOriginalFilename(), FALLBACK_FILE_NAME);
        DocumentFormat format;
        String sha256;
        try (DigestInputStream content = new DigestInputStream(file.getInputStream(), sha256Digest())) {
            byte[] header = content.readNBytes(DocumentFormat.HEADER_LENGTH);
            format = DocumentFormat.detect(fileName, header).orElseThrow(() -> new ApiException(
                    ErrorCode.UNSUPPORTED_FILE_TYPE, "rejected " + DocumentFormat.extensionOf(fileName)
                    + " upload whose bytes match no accepted format"));
            content.transferTo(OutputStream.nullOutputStream());
            sha256 = HexFormat.of().formatHex(content.getMessageDigest().digest());
        } catch (IOException failure) {
            throw new UncheckedIOException("could not read an uploaded person document", failure);
        }
        versions.findFirstByPersonIdAndSha256(personId, sha256).ifPresent(existing -> {
            throw ApiException.withProperty(ErrorCode.PERSON_DOCUMENT_DUPLICATE, "duplicateOf",
                    Map.of("documentId", existing.getDocumentId(), "versionNo", existing.getVersionNo()));
        });
        String key = "ws/" + workspaceId + "/people/" + personId + "/documents/" + UUID.randomUUID();
        try (InputStream content = file.getInputStream()) {
            store.put(key, content, file.getSize(), format.contentType());
        } catch (IOException failure) {
            throw new UncheckedIOException("could not store an uploaded person document", failure);
        }
        return new StagedDocumentFile(fileName, format, file.getSize(), sha256, key);
    }

    /** An object left behind costs storage, never correctness: no row points at it any more. */
    private void deleteQuietly(String key) {
        try {
            store.delete(key);
        } catch (IOException | RuntimeException failure) {
            log.warn("Could not delete stored document {}; it is unreferenced and safe to sweep", key, failure);
        }
    }

    private PersonDocumentResponse cardOf(UUID userId, UUID workspaceId, PersonDocument document) {
        List<PersonDocumentVersion> stack = versions.findByDocumentIdOrderByVersionNoDesc(document.getId());
        return toDto(document, stack, usersOf(List.of(document), stack), userId, mayRemoveAny(userId, workspaceId));
    }

    private boolean mayRemoveAny(UUID userId, UUID workspaceId) {
        return workspaceAccess.holdsAction(userId, workspaceId, WorkspaceAction.WORKSPACE_MANAGE);
    }

    private Map<UUID, User> usersOf(List<PersonDocument> found, List<PersonDocumentVersion> files) {
        List<UUID> ids = Stream.concat(found.stream().map(PersonDocument::getCreatedBy),
                        files.stream().map(PersonDocumentVersion::getUploadedBy))
                .filter(Objects::nonNull).distinct().toList();
        return users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private static PersonDocumentResponse toDto(PersonDocument document, List<PersonDocumentVersion> stack,
                                                Map<UUID, User> named, UUID userId, boolean mayRemoveAny) {
        User filer = named.get(document.getCreatedBy());
        List<PersonDocumentVersionResponse> files = stack.stream().map(version -> {
            User uploader = named.get(version.getUploadedBy());
            return new PersonDocumentVersionResponse(version.getId(), version.getVersionNo(), version.getFileName(),
                    version.getContentType(), version.getSizeBytes(), version.getUploadedBy(),
                    uploader == null ? null : uploader.getFullName(), version.getUploadedAt(),
                    isPreviewable(version.getContentType()), mayRemoveAny || version.isUploadedBy(userId));
        }).toList();
        return new PersonDocumentResponse(document.getId(), document.getCategory().value(), document.getTitle(),
                document.isPrimaryCv(), document.getProjectId(), document.getProjectTitle(), document.getCreatedBy(),
                filer == null ? null : filer.getFullName(), document.getCreatedAt(), document.getUpdatedAt(),
                mayRemoveAny || document.isFiledBy(userId), files);
    }

    private static boolean isPreviewable(String contentType) {
        return Arrays.stream(DocumentFormat.values())
                .anyMatch(format -> format.previewable() && format.contentType().equals(contentType));
    }

    private static Instant latestUploadOf(PersonDocumentResponse card) {
        return card.versions().isEmpty() ? Instant.EPOCH : card.versions().getFirst().uploadedAt();
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("every JVM ships SHA-256", impossible);
        }
    }
}
