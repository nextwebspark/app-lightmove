package app.lightmove.api.core.storage.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Private storage for uploaded files, addressed by a key the caller builds and the database keeps.
 * Nothing here knows who may read a file: every read reaches it through a service that has already
 * answered that, and no key is ever handed to a browser.
 */
public interface DocumentStore {

    void put(String key, InputStream content, long sizeBytes, String contentType) throws IOException;

    /**
     * A file written as it is produced, for one too large to hold first. It is in place once the stream closes; a
     * writer that fails part-way closes it and deletes the key, since a closed stream is a file whatever it holds.
     */
    OutputStream create(String key, String contentType) throws IOException;

    InputStream open(String key) throws IOException;

    /** Gone already is not a failure: a delete retried after a crash must still succeed. */
    void delete(String key) throws IOException;

    /**
     * Every file whose key starts with {@code prefix} — a whole workspace's, {@code ws/{id}/} — answering how many went.
     * The prefix must end at a {@code /}, so one workspace's id can never match the start of another's.
     */
    long deletePrefix(String prefix) throws IOException;

    static void requireFolderPrefix(String prefix) {
        if (prefix == null || prefix.length() < 2 || !prefix.endsWith("/") || prefix.startsWith("/")) {
            throw new IllegalArgumentException("a storage prefix ends at a folder: " + prefix);
        }
    }
}
