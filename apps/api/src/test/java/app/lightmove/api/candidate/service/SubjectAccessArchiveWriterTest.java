package app.lightmove.api.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.lightmove.api.candidate.model.StoredDocumentFile;
import app.lightmove.api.candidate.model.SubjectAccessArchive;
import app.lightmove.api.candidate.model.SubjectAccessFile;
import app.lightmove.api.candidate.model.SubjectAccessReferences;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SubjectAccessArchiveWriterTest {

    private final PersonDocumentService documents = mock(PersonDocumentService.class);
    private final SubjectAccessArchiveWriter writer =
            new SubjectAccessArchiveWriter(documents, JsonMapper.builder().build());

    @Test
    @DisplayName("a document the bucket cannot give back is left out and named; the rest of the export still goes")
    void anUnreadableDocumentIsNamedNotFatal() throws Exception {
        UUID missingVersion = UUID.randomUUID();
        StoredDocumentFile readable = stored("Offer.pdf", "ws/a/readable");
        StoredDocumentFile gone = stored("Noor_Haddad_CV.pdf", "ws/a/gone");
        StoredDocumentFile cutOff = stored("Reference.pdf", "ws/a/cut-off");
        when(documents.open(argThat(file -> file != null && file.storageKey().equals("ws/a/readable"))))
                .thenReturn(new ByteArrayInputStream("%PDF-1.7 offer".getBytes(StandardCharsets.US_ASCII)));
        when(documents.open(argThat(file -> file != null && file.storageKey().equals("ws/a/gone"))))
                .thenThrow(new UncheckedIOException(new IOException("no such object")));
        when(documents.open(argThat(file -> file != null && file.storageKey().equals("ws/a/cut-off"))))
                .thenReturn(failingMidway());

        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        writer.write(archiveOf(List.of(
                SubjectAccessFile.ofBytes("photo.jpg", new byte[] {1, 2, 3}),
                SubjectAccessFile.ofStored("documents/offer-Offer.pdf", UUID.randomUUID(), readable),
                SubjectAccessFile.ofStored("documents/cv-Noor_Haddad_CV.pdf", missingVersion, gone),
                SubjectAccessFile.ofStored("documents/reference-Reference.pdf", UUID.randomUUID(), cutOff))), zip);

        Map<String, byte[]> entries = unzip(zip.toByteArray());
        assertThat(entries).containsKeys("README.txt", "person.json", "person.html", "photo.jpg",
                "documents/offer-Offer.pdf", SubjectAccessArchiveWriter.UNREADABLE_LIST);
        assertThat(entries).doesNotContainKeys("documents/cv-Noor_Haddad_CV.pdf",
                "documents/reference-Reference.pdf");
        assertThat(new String(entries.get("documents/offer-Offer.pdf"), StandardCharsets.US_ASCII))
                .isEqualTo("%PDF-1.7 offer");
        assertThat(new String(entries.get(SubjectAccessArchiveWriter.UNREADABLE_LIST), StandardCharsets.UTF_8))
                .contains("Noor_Haddad_CV.pdf (document version " + missingVersion + ")")
                .contains("Reference.pdf");
    }

    @Test
    @DisplayName("an export whose files all read lists nothing as left out")
    void nothingLeftOutListsNothing() throws Exception {
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        writer.write(archiveOf(List.of(SubjectAccessFile.ofBytes("photo.jpg", new byte[] {1}))), zip);

        assertThat(unzip(zip.toByteArray())).doesNotContainKey(SubjectAccessArchiveWriter.UNREADABLE_LIST);
    }

    private static StoredDocumentFile stored(String fileName, String storageKey) {
        return new StoredDocumentFile(fileName, "application/pdf", 10, storageKey, false);
    }

    private static InputStream failingMidway() {
        return new InputStream() {
            private int served;

            @Override
            public int read() throws IOException {
                if (served++ < 4) {
                    return 'x';
                }
                throw new IOException("connection reset");
            }
        };
    }

    private static SubjectAccessArchive archiveOf(List<SubjectAccessFile> files) {
        SubjectAccessReferences references = new SubjectAccessReferences(Map.of(), Map.of(), Map.of(), Map.of());
        return new SubjectAccessArchive("Noor Haddad", "Answering Firm", "Alok Kumar",
                Instant.parse("2026-11-12T09:00:00Z"),
                SubjectAccessStatements.aboutOf(new SubjectAccessStatements.Firm("Answering Firm", false, null),
                        Map.of(), Set.of(), 30),
                List.of(), List.of(), references, files);
    }

    private static Map<String, byte[]> unzip(byte[] archive) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }
}
