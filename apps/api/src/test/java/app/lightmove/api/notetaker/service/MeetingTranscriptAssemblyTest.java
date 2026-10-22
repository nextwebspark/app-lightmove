package app.lightmove.api.notetaker.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.notetaker.model.CaptionedLine;
import app.lightmove.api.notetaker.model.HeardLine;
import app.lightmove.api.notetaker.model.TranscribedPiece;
import app.lightmove.api.notetaker.model.TranscriptLine;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MeetingTranscriptAssemblyTest {

    private static final Instant START = Instant.parse("2026-10-22T10:00:00Z");

    @Test
    @DisplayName("each piece's voices are named from the captions said at the same time, piece by piece")
    void namesVoicesFromCaptions() {
        List<CaptionedLine> captions = List.of(
                new CaptionedLine(1, "Priya Raman", "I led the finance transformation in Riyadh",
                        START.plusSeconds(2), START.plusSeconds(8)),
                new CaptionedLine(2, "Omar Saleh", "What drew you to the group role", START.plusSeconds(32),
                        START.plusSeconds(36)));
        // "Speaker 1" is Priya in the first piece and Omar in the second; a voice no caption matches keeps its label.
        List<TranscribedPiece> pieces = List.of(
                new TranscribedPiece(2, START.plusSeconds(30), List.of(
                        new HeardLine(2_000, "Speaker 1", "What drew you to the group role?"),
                        new HeardLine(9_000, "Speaker 2", "Mostly scale."))),
                new TranscribedPiece(1, START, List.of(
                        new HeardLine(2_000, "Speaker 1", "I led the finance transformation in Riyadh."))));

        List<TranscriptLine> transcript = MeetingTranscriptAssembly.assemble(pieces, captions);

        assertThat(transcript).extracting(TranscriptLine::speaker)
                .containsExactly("Priya Raman", "Omar Saleh", "Speaker 2");
        assertThat(transcript.getFirst().at()).isEqualTo(START.plusSeconds(2));
    }

    @Test
    @DisplayName("a line heard in the second both pieces recorded is kept by the piece it began in")
    void dropsTheOverlap() {
        List<TranscribedPiece> pieces = List.of(
                new TranscribedPiece(1, START, List.of(
                        new HeardLine(10_000, "Speaker 1", "Before the boundary."),
                        new HeardLine(30_200, "Speaker 1", "Across the"))),
                new TranscribedPiece(2, START.plusSeconds(30), List.of(
                        new HeardLine(100, "Speaker 1", "Across the boundary."))));

        assertThat(MeetingTranscriptAssembly.assemble(pieces, List.of())).extracting(TranscriptLine::text)
                .containsExactly("Before the boundary.", "Across the boundary.");
    }
}
