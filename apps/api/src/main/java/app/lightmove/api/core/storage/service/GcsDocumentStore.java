package app.lightmove.api.core.storage.service;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import lombok.RequiredArgsConstructor;

/**
 * Files in one private Cloud Storage bucket, read and written as the Cloud Run service account. The
 * bucket enforces public access prevention, so a key that leaked would still open nothing.
 */
@RequiredArgsConstructor
public class GcsDocumentStore implements DocumentStore {

    private final Storage storage;
    private final String bucket;

    @Override
    public void put(String key, InputStream content, long sizeBytes, String contentType) throws IOException {
        BlobInfo blob = BlobInfo.newBuilder(BlobId.of(bucket, key)).setContentType(contentType).build();
        try {
            storage.createFrom(blob, content, Storage.BlobWriteOption.doesNotExist());
        } catch (StorageException failure) {
            throw new IOException("could not write " + key + " to the document bucket", failure);
        }
    }

    @Override
    public InputStream open(String key) throws IOException {
        try {
            return Channels.newInputStream(storage.reader(BlobId.of(bucket, key)));
        } catch (StorageException failure) {
            throw new IOException("could not read " + key + " from the document bucket", failure);
        }
    }

    @Override
    public void delete(String key) throws IOException {
        try {
            storage.delete(BlobId.of(bucket, key));
        } catch (StorageException failure) {
            throw new IOException("could not delete " + key + " from the document bucket", failure);
        }
    }
}
