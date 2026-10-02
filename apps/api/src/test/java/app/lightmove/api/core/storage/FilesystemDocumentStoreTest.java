package app.lightmove.api.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.core.storage.service.FilesystemDocumentStore;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemDocumentStoreTest {

    @TempDir Path root;

    @Test
    @DisplayName("writes, reads back and deletes a file, and deleting it twice is no failure")
    void roundTrips() throws Exception {
        FilesystemDocumentStore store = new FilesystemDocumentStore(root);
        byte[] content = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);

        store.put("ws/a/people/b/documents/c", new ByteArrayInputStream(content), content.length, "application/pdf");
        try (InputStream read = store.open("ws/a/people/b/documents/c")) {
            assertThat(read.readAllBytes()).isEqualTo(content);
        }
        store.delete("ws/a/people/b/documents/c");
        store.delete("ws/a/people/b/documents/c");
        assertThat(root.resolve("ws/a/people/b/documents/c")).doesNotExist();
    }

    @Test
    @DisplayName("refuses a key that would resolve outside its root")
    void refusesAnEscapingKey() {
        FilesystemDocumentStore store = new FilesystemDocumentStore(root);
        assertThatThrownBy(() -> store.open("../outside")).isInstanceOf(IllegalArgumentException.class);
    }
}
