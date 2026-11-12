package app.lightmove.api.candidate.model;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A file the export carries beside its JSON: either bytes already read from the database (a photo), or a document
 * version in the bucket, opened only while the zip is written.
 */
public record SubjectAccessFile(String path, byte @Nullable [] content, @Nullable StoredDocumentFile stored,
                                @Nullable UUID versionId) {

    public static SubjectAccessFile ofBytes(String path, byte[] content) {
        return new SubjectAccessFile(path, content, null, null);
    }

    public static SubjectAccessFile ofStored(String path, UUID versionId, StoredDocumentFile stored) {
        return new SubjectAccessFile(path, null, stored, versionId);
    }
}
