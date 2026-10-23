package app.lightmove.api.enrichment.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.lightmove.api.candidate.constant.PersonDocumentCategory;
import app.lightmove.api.candidate.constant.PersonNoteKind;
import app.lightmove.api.candidate.model.CandidateMaterial;
import app.lightmove.api.candidate.model.DocumentOnFile;
import app.lightmove.api.candidate.model.NoteOnFile;
import app.lightmove.api.candidate.model.SpokenLine;
import app.lightmove.api.candidate.model.TranscriptOnFile;
import app.lightmove.api.candidate.model.StoredDocumentFile;
import app.lightmove.api.candidate.service.CandidateMaterialService;
import app.lightmove.api.core.storage.constant.DocumentFormat;
import app.lightmove.api.position.service.PositionDocumentTextReader;
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

    @Test
    @DisplayName("a call's transcript reaches the assessment as speaker turns, its contacts taken out")
    void transcriptIsWrittenAsTurnsWithContactsRedacted() {
        Instant start = Instant.parse("2026-10-12T14:00:00Z");
        when(material.materialOf(PROJECT, CANDIDATE)).thenReturn(CandidateMaterial.NONE);
        when(material.transcriptsOf(PROJECT, CANDIDATE)).thenReturn(List.of(new TranscriptOnFile("First conversation",
                start, List.of(
                        new SpokenLine(start.plusSeconds(5), "Yara Haddad", "Tell me about the IPO."),
                        new SpokenLine(start.plusSeconds(65), "Priya Raman", "I led the finance team through it."),
                        new SpokenLine(start.plusSeconds(70), "Priya Raman", "Write to priya@example.com."),
                        new SpokenLine(start.plusSeconds(3_725), "Yara Haddad", "Thanks.")))));

        MaterialText text = reader.readForAssessment(PROJECT, CANDIDATE);

        assertThat(text.transcriptCount()).isEqualTo(1);
        assertThat(text.transcripts()).startsWith("=== Call: First conversation · 12 Oct 2026 ===")
                .contains("[00:05] Yara Haddad: Tell me about the IPO.",
                        "[01:05] Priya Raman: I led the finance team through it. Write to",
                        "[1:02:05] Yara Haddad: Thanks.")
                .doesNotContain("priya@example.com");
    }

    @Test
    @DisplayName("a long call keeps its start and end, a call past the budget is left out, and Background reads none")
    void transcriptsShareTheBudget() {
        Instant start = Instant.parse("2026-10-12T14:00:00Z");
        TranscriptOnFile longCall = new TranscriptOnFile("Long call", start, List.of(
                new SpokenLine(start, "Priya Raman", "OPENING " + "a".repeat(40_000)),
                new SpokenLine(start.plusSeconds(60), "Yara Haddad", "b".repeat(40_000) + " CLOSING")));
        when(material.materialOf(PROJECT, CANDIDATE)).thenReturn(CandidateMaterial.NONE);
        when(material.transcriptsOf(PROJECT, CANDIDATE)).thenReturn(List.of(longCall, longCall, longCall));

        MaterialText text = reader.readForAssessment(PROJECT, CANDIDATE);

        assertThat(text.transcriptCount()).isEqualTo(2);
        assertThat(text.transcripts()).contains("OPENING", "CLOSING", "the middle of the call is left out")
                .endsWith(CandidateMaterialReader.EARLIER_CALLS_LEFT_OUT);
        assertThat(text.transcripts().length()).isLessThanOrEqualTo(CandidateMaterialReader.MAX_TRANSCRIPTS + 100);
        assertThat(reader.read(PROJECT, CANDIDATE).transcripts()).isEqualTo(MaterialText.NONE_ON_FILE);
    }

    private static DocumentOnFile document(String title, PersonDocumentCategory category, DocumentFormat format) {
        return new DocumentOnFile(title, category, category == PersonDocumentCategory.CV, Instant.now(),
                new StoredDocumentFile(title, format.contentType(), 10, "key-" + title, false));
    }
}
