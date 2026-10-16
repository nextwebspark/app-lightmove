package app.uncava.api.enrichment.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.uncava.api.candidate.constant.PersonDocumentCategory;
import app.uncava.api.candidate.constant.PersonNoteKind;
import app.uncava.api.candidate.model.CandidateMaterial;
import app.uncava.api.candidate.model.DocumentOnFile;
import app.uncava.api.candidate.model.NoteOnFile;
import app.uncava.api.candidate.model.StoredDocumentFile;
import app.uncava.api.candidate.service.CandidateMaterialService;
import app.uncava.api.core.storage.constant.DocumentFormat;
import app.uncava.api.position.service.PositionDocumentTextReader;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CandidateMaterialReaderTest {

    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID CANDIDATE = UUID.randomUUID();

    private final CandidateMaterialService material = mock(CandidateMaterialService.class);
    private final PositionDocumentTextReader textReader = mock(PositionDocumentTextReader.class);
    private final CandidateMaterialReader reader = new CandidateMaterialReader(material, textReader);

    @Test
    @DisplayName("two long documents share the budget: the second is cut, never dropped, and an image is left out")
    void longDocumentsShareTheBudget() {
        when(material.materialOf(PROJECT, CANDIDATE)).thenReturn(new CandidateMaterial(List.of(), List.of(
                document("CV", PersonDocumentCategory.CV, DocumentFormat.PDF),
                document("Photo", PersonDocumentCategory.OTHER, DocumentFormat.PNG),
                document("Reference", PersonDocumentCategory.REFERENCE, DocumentFormat.DOCX))));
        when(material.open(any())).thenAnswer(call -> new ByteArrayInputStream(new byte[] {1}));
        when(textReader.readUpTo(any(), anyInt()))
                .thenAnswer(call -> "x".repeat(Math.min(25_000, (int) call.getArgument(1))));

        MaterialText text = reader.read(PROJECT, CANDIDATE);

        assertThat(text.documentCount()).isEqualTo(2);
        assertThat(text.documents()).contains("=== CV: CV ===", "=== Reference: Reference ===")
                .doesNotContain("Photo");
        assertThat(text.documents().length()).isLessThanOrEqualTo(CandidateMaterialReader.MAX_DOCUMENTS + 2);
    }

    @Test
    @DisplayName("notes past the budget are named as left out, and only the notes sent are counted")
    void notesOverTheBudgetAreLeftOutAndNotCounted() {
        List<NoteOnFile> notes = IntStream.range(0, 20)
                .mapToObj(day -> new NoteOnFile(PersonNoteKind.CALL, Instant.parse("2026-10-01T09:00:00Z"), null,
                        "Call " + day + " " + "y".repeat(1_500)))
                .toList();
        when(material.materialOf(PROJECT, CANDIDATE)).thenReturn(new CandidateMaterial(notes, List.of()));

        MaterialText text = reader.read(PROJECT, CANDIDATE);

        assertThat(text.notes()).endsWith(CandidateMaterialReader.OLDER_NOTES_LEFT_OUT);
        assertThat(text.noteCount()).isLessThan(20).isEqualTo(text.notes().split("\n").length - 1);
        assertThat(text.documents()).isEqualTo(MaterialText.NONE_ON_FILE);
    }

    private static DocumentOnFile document(String title, PersonDocumentCategory category, DocumentFormat format) {
        return new DocumentOnFile(title, category, category == PersonDocumentCategory.CV, Instant.now(),
                new StoredDocumentFile(title, format.contentType(), 10, "key-" + title, false));
    }
}
