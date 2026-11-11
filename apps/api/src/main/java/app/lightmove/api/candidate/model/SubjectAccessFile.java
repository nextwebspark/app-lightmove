package app.lightmove.api.candidate.model;

import org.jspecify.annotations.Nullable;

/**
 * A file the export carries beside its JSON: either bytes already read from the database (a photo), or a document
 * in the bucket, opened only while the zip is written.
 */
public record SubjectAccessFile(String path, byte @Nullable [] content, @Nullable StoredDocumentFile stored) {

    public static SubjectAccessFile ofBytes(String path, byte[] content) {
        return new SubjectAccessFile(path, content, null);
    }

    public static SubjectAccessFile ofStored(String path, StoredDocumentFile stored) {
        return new SubjectAccessFile(path, null, stored);
    }
}
