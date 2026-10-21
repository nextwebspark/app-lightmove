package app.lightmove.api.outreach.service;

import app.lightmove.api.candidate.model.AttachableDocument;
import app.lightmove.api.candidate.service.CandidateOutreachService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.OutreachAttachmentSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.storage.constant.DocumentFormat;
import app.lightmove.api.core.storage.service.DocumentStore;
import app.lightmove.api.core.text.service.FileNameSanitizer;
import app.lightmove.api.outreach.model.EmailAttachment;
import app.lightmove.api.outreach.model.OutreachMessage;
import app.lightmove.api.outreach.model.OutreachMessageAttachment;
import app.lightmove.api.outreach.model.StagedAttachment;
import app.lightmove.api.outreach.repository.OutreachMessageAttachmentRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * The files a consultant's own email carries. Each is written to storage under the email's own key before the email is
 * saved — an upload as sent, a person's document copied from its version — so what goes is what was attached, whatever
 * happens to that document meanwhile. The bytes are read back only to send and to download, and deleted once an email
 * is cancelled.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutreachAttachments {

    private static final String FALLBACK_FILE_NAME = "attachment";

    private final OutreachMessageAttachmentRepository attachments;
    private final CandidateOutreachService people;
    private final DocumentStore store;
    private final LightMoveProperties properties;

    /**
     * Every file checked — how many, each one's size and type, and all of them together — before any is stored, and
     * every one stored before the email is; whatever fails takes what was already stored with it.
     */
    public List<StagedAttachment> stage(UUID workspaceId, UUID projectId, UUID candidateId, List<MultipartFile> uploads,
                                        List<UUID> documentVersionIds) {
        List<MultipartFile> files = uploads == null ? List.of() : uploads;
        List<UUID> versionIds = documentVersionIds == null ? List.of()
                : List.copyOf(new LinkedHashSet<>(documentVersionIds));
        if (files.size() + versionIds.size() > limits().maxFiles()) {
            throw new ApiException(ErrorCode.OUTREACH_TOO_MANY_ATTACHMENTS,
                    (files.size() + versionIds.size()) + " files exceed " + limits().maxFiles());
        }
        List<AttachableDocument> documents = versionIds.stream()
                .map(versionId -> people.attachableDocumentOf(workspaceId, projectId, candidateId, versionId)
                        .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND)))
                .toList();
        long total = 0;
        for (MultipartFile file : files) {
            total += requireWithinFileLimit(file.getSize());
        }
        for (AttachableDocument document : documents) {
            total += requireWithinFileLimit(document.file().sizeBytes());
        }
        if (total > limits().maxTotalBytes()) {
            throw new ApiException(ErrorCode.OUTREACH_ATTACHMENTS_TOO_LARGE,
                    "attachments of " + total + " bytes exceed " + limits().maxTotalBytes());
        }

        List<StagedAttachment> staged = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                staged.add(stageUpload(workspaceId, file));
            }
            for (AttachableDocument document : documents) {
                staged.add(stageDocument(workspaceId, document));
            }
        } catch (RuntimeException failed) {
            discard(staged);
            throw failed;
        }
        return staged;
    }

    /** The rows, in the transaction that saves the email. */
    public void record(OutreachMessage message, List<StagedAttachment> staged) {
        for (int position = 0; position < staged.size(); position++) {
            attachments.save(new OutreachMessageAttachment(message, position, staged.get(position)));
        }
    }

    /** Stored files no row will point at: the email they were for was refused. */
    public void discard(Collection<StagedAttachment> staged) {
        staged.forEach(file -> deleteQuietly(file.storageKey()));
    }

    /** An email's files as the mail service takes them, read from storage outside any transaction. */
    public List<EmailAttachment> contentOf(UUID messageId) {
        return attachments.findByMessageIdOrderByPosition(messageId).stream()
                .map(attachment -> new EmailAttachment(attachment.getFileName(), attachment.getContentType(),
                        bytesOf(attachment)))
                .toList();
    }

    public List<OutreachMessageAttachment> of(UUID messageId) {
        return attachments.findByMessageIdOrderByPosition(messageId);
    }

    public InputStream open(OutreachMessageAttachment attachment) {
        try {
            return store.open(attachment.getStorageKey());
        } catch (IOException failure) {
            throw new UncheckedIOException("could not open an outreach attachment", failure);
        }
    }

    /**
     * A cancelled email's files, deleted once the cancel commits: a rolled-back cancel leaves an email that still
     * goes, and must still find them.
     */
    public void deleteFilesOnCommit(UUID messageId) {
        List<String> keys = attachments.findByMessageIdOrderByPosition(messageId).stream()
                .map(OutreachMessageAttachment::getStorageKey)
                .toList();
        if (keys.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            keys.forEach(this::deleteQuietly);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                keys.forEach(OutreachAttachments.this::deleteQuietly);
            }
        });
    }

    public OutreachAttachmentSettings limits() {
        return properties.outreach().attachments();
    }

    private long requireWithinFileLimit(long sizeBytes) {
        if (sizeBytes > limits().maxFileBytes()) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE,
                    "attachment of " + sizeBytes + " bytes exceeds " + limits().maxFileBytes());
        }
        return sizeBytes;
    }

    /** Held in memory whole: one file is at most a few megabytes, and the mail service takes it as bytes anyway. */
    private StagedAttachment stageUpload(UUID workspaceId, MultipartFile file) {
        if (file.isEmpty()) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED, "One of the files is empty");
        }
        String fileName = FileNameSanitizer.sanitize(file.getOriginalFilename(), FALLBACK_FILE_NAME);
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException failure) {
            throw new UncheckedIOException("could not read an uploaded outreach attachment", failure);
        }
        DocumentFormat format = DocumentFormat.detect(fileName,
                        Arrays.copyOf(content, Math.min(content.length, DocumentFormat.HEADER_LENGTH)))
                .orElseThrow(() -> new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE, "rejected "
                        + DocumentFormat.extensionOf(fileName) + " attachment whose bytes match no accepted format"));
        String key = put(workspaceId, content, format.contentType());
        return new StagedAttachment(fileName, format.contentType(), content.length, sha256Of(content), key, null);
    }

    private StagedAttachment stageDocument(UUID workspaceId, AttachableDocument document) {
        byte[] content;
        try (InputStream source = people.openDocument(document)) {
            content = source.readAllBytes();
        } catch (IOException failure) {
            throw new UncheckedIOException("could not read a person document to attach", failure);
        }
        String key = put(workspaceId, content, document.file().contentType());
        return new StagedAttachment(document.file().fileName(), document.file().contentType(), content.length,
                document.sha256(), key, document.versionId());
    }

    private String put(UUID workspaceId, byte[] content, String contentType) {
        String key = "ws/" + workspaceId + "/outreach/attachments/" + UUID.randomUUID();
        try {
            store.put(key, new ByteArrayInputStream(content), content.length, contentType);
        } catch (IOException failure) {
            throw new UncheckedIOException("could not store an outreach attachment", failure);
        }
        return key;
    }

    private byte[] bytesOf(OutreachMessageAttachment attachment) {
        try (InputStream content = store.open(attachment.getStorageKey())) {
            return content.readAllBytes();
        } catch (IOException failure) {
            throw new UncheckedIOException("could not read an outreach attachment to send", failure);
        }
    }

    /** An object left behind costs storage, never correctness: no row will read it. */
    private void deleteQuietly(String key) {
        try {
            store.delete(key);
        } catch (IOException | RuntimeException failure) {
            log.warn("Could not delete outreach attachment {}; it is unreferenced and safe to sweep", key, failure);
        }
    }

    private static String sha256Of(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required of every JVM", impossible);
        }
    }
}
