package app.lightmove.api.core.storage.service;

import java.io.IOException;
import java.io.InputStream;

/**
 * Private storage for uploaded files, addressed by a key the caller builds and the database keeps.
 * Nothing here knows who may read a file: every read reaches it through a service that has already
 * answered that, and no key is ever handed to a browser.
 */
public interface DocumentStore {

    void put(String key, InputStream content, long sizeBytes, String contentType) throws IOException;

    InputStream open(String key) throws IOException;

    /** Gone already is not a failure: a delete retried after a crash must still succeed. */
    void delete(String key) throws IOException;
}
