package app.lightmove.api.core.storage.service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * Files in one private Cloud Storage bucket, read and written as the Cloud Run service account. The
 * bucket enforces public access prevention, so a key that leaked would still open nothing.
 */
@RequiredArgsConstructor
public class GcsDocumentStore implements DocumentStore {

    /** The JSON API's batch ceiling. */
    private static final int DELETE_BATCH = 100;

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
    public OutputStream create(String key, String contentType) throws IOException {
        BlobInfo blob = BlobInfo.newBuilder(BlobId.of(bucket, key)).setContentType(contentType).build();
        try {
            return Channels.newOutputStream(storage.writer(blob, Storage.BlobWriteOption.doesNotExist()));
        } catch (StorageException failure) {
            throw new IOException("could not start writing " + key + " to the document bucket", failure);
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

    @Override
    public long deletePrefix(String prefix) throws IOException {
        DocumentStore.requireFolderPrefix(prefix);
        try {
            long deleted = 0;
            List<BlobId> batch = new ArrayList<>(DELETE_BATCH);
            for (Blob blob : storage.list(bucket, Storage.BlobListOption.prefix(prefix)).iterateAll()) {
                batch.add(blob.getBlobId());
                if (batch.size() == DELETE_BATCH) {
                    deleted += deleteAll(batch);
                    batch.clear();
                }
            }
            return batch.isEmpty() ? deleted : deleted + deleteAll(batch);
        } catch (StorageException failure) {
            throw new IOException("could not delete the files under " + prefix + " from the document bucket", failure);
        }
    }

    private long deleteAll(List<BlobId> blobs) {
        return storage.delete(blobs).stream().filter(Boolean.TRUE::equals).count();
    }
}
