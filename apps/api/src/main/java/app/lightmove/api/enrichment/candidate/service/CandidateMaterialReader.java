package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.model.CandidateMaterial;
import app.lightmove.api.candidate.model.DocumentOnFile;
import app.lightmove.api.candidate.model.NoteOnFile;
import app.lightmove.api.candidate.model.SpokenLine;
import app.lightmove.api.candidate.model.TranscriptOnFile;
import app.lightmove.api.candidate.service.CandidateMaterialService;
import app.lightmove.api.common.transcript.CallTranscriptText;
import app.lightmove.api.core.llm.service.ContactRedaction;
import app.lightmove.api.core.storage.constant.DocumentFormat;
import app.lightmove.api.position.service.PositionDocumentTextReader;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Writes out what the team has filed on a person for a prompt: every note the position reads, newest first, and the
 * text of the latest file of every document a text can be read from — an image or a scan without a text layer is left
 * out — and, for the assessment alone, the transcripts of the calls the position recorded with them. Emails and phone
 * numbers are taken out of all three, and each is held to a share of the prompt, so one long file or call never crowds
 * out the rest.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class CandidateMaterialReader {

    static final int MAX_NOTE = 2_000;
    static final int MAX_NOTES = 12_000;
    static final int MAX_DOCUMENT = 20_000;
    static final int MAX_DOCUMENTS = 40_000;
    static final String CUT = " … [cut]";
    static final String OLDER_NOTES_LEFT_OUT = "- older notes left out";
    /** About two and a half hours of talk, so a typical call of an hour or ninety minutes is read whole. */
    static final int MAX_TRANSCRIPT = 150_000;
    static final int MAX_TRANSCRIPTS = 250_000;
    static final int LEAST_TRANSCRIPT = 2_000;
    static final String TEAM_MARK = "(your team)";
    static final String EARLIER_CALLS_LEFT_OUT = "- earlier calls left out";

    private static final Set<String> READABLE = Stream.of(DocumentFormat.PDF, DocumentFormat.DOCX, DocumentFormat.DOC,
            DocumentFormat.TXT).map(DocumentFormat::contentType).collect(Collectors.toSet());
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private final CandidateMaterialService material;
    private final PositionDocumentTextReader textReader;

    MaterialText read(UUID projectId, UUID candidateId) {
        CandidateMaterial filed = material.materialOf(projectId, candidateId);
        List<String> notes = notesOf(filed.notes());
        List<String> documents = documentsOf(filed.documents());
        int notesSent = notes.size() - (notes.contains(OLDER_NOTES_LEFT_OUT) ? 1 : 0);
        return new MaterialText(joined(notes, "\n"), joined(documents, "\n\n"), notesSent, documents.size());
    }

    MaterialText readForAssessment(UUID projectId, UUID candidateId) {
        List<String> calls = transcriptsOf(material.transcriptsOf(projectId, candidateId));
        int callsSent = calls.size() - (calls.contains(EARLIER_CALLS_LEFT_OUT) ? 1 : 0);
        return read(projectId, candidateId).withTranscripts(joined(calls, "\n\n"), callsSent);
    }

    /**
     * Newest call first; one too long for its share keeps its start and its end, where the opening and the closing
     * questions — motivation, notice, what happens next — usually sit.
     */
    private static List<String> transcriptsOf(List<TranscriptOnFile> transcripts) {
        List<String> read = new ArrayList<>();
        int left = MAX_TRANSCRIPTS;
        for (TranscriptOnFile transcript : transcripts) {
            String title = transcript.title() == null || transcript.title().isBlank() ? "untitled"
                    : transcript.title().strip();
            String heading = "=== Call: %s · %s ===\n".formatted(title, DAY.format(transcript.startsAt()));
            int room = Math.min(MAX_TRANSCRIPT,
                    left - heading.length() - CallTranscriptText.MIDDLE_LEFT_OUT.length());
            if (room < LEAST_TRANSCRIPT) {
                read.add(EARLIER_CALLS_LEFT_OUT);
                break;
            }
            String block = heading + CallTranscriptText.keepStartAndEnd(
                    ContactRedaction.redact(turnsOf(transcript)), room);
            read.add(block);
            left -= block.length();
        }
        return read;
    }

    /** A speaker's consecutive lines are one turn, stamped with when it began; the team's speakers are marked. */
    private static String turnsOf(TranscriptOnFile transcript) {
        StringBuilder text = new StringBuilder();
        String speaking = null;
        for (SpokenLine line : transcript.lines()) {
            if (line.text() == null || line.text().isBlank()) {
                continue;
            }
            String speaker = speakerOf(line);
            if (speaker.equals(speaking)) {
                text.append(' ').append(line.text().strip());
                continue;
            }
            speaking = speaker;
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append('[').append(CallTranscriptText.elapsed(transcript.startsAt(), line.at())).append("] ")
                    .append(speaker).append(": ").append(line.text().strip());
        }
        return text.toString();
    }

    private static String speakerOf(SpokenLine line) {
        String name = line.speaker() == null || line.speaker().isBlank() ? "Unnamed speaker" : line.speaker().strip();
        return line.team() ? name + " " + TEAM_MARK : name;
    }

    private static List<String> notesOf(List<NoteOnFile> notes) {
        List<String> lines = new ArrayList<>();
        int total = 0;
        for (NoteOnFile note : notes) {
            String line = "- [%s · %s%s] %s".formatted(note.kind().promptLabel(), DAY.format(note.writtenAt()),
                    note.aboutPosition() == null ? "" : " · about " + note.aboutPosition(),
                    cut(ContactRedaction.redact(note.body().strip()), MAX_NOTE));
            if (total + line.length() > MAX_NOTES) {
                lines.add(OLDER_NOTES_LEFT_OUT);
                break;
            }
            lines.add(line);
            total += line.length();
        }
        return lines;
    }

    /** A file that still fits is cut to what is left of the budget, so a long one never shuts out the next. */
    private List<String> documentsOf(List<DocumentOnFile> documents) {
        List<String> read = new ArrayList<>();
        int left = MAX_DOCUMENTS;
        for (DocumentOnFile document : documents) {
            String heading = "=== %s: %s ===\n".formatted(labelOf(document.category().name()), document.title());
            int room = Math.min(MAX_DOCUMENT, left - heading.length() - CUT.length());
            if (room <= 0) {
                break;
            }
            if (!READABLE.contains(document.file().contentType())) {
                continue;
            }
            String text = textOf(document, room);
            if (text == null) {
                continue;
            }
            String block = heading + cut(ContactRedaction.redact(text), room);
            read.add(block);
            left -= block.length();
        }
        return read;
    }

    /** A file that will not open or read is left out, never the run: the rest of what is on file still counts. */
    private String textOf(DocumentOnFile document, int maxCharacters) {
        try (InputStream in = material.open(document.file())) {
            return textReader.readUpTo(in.readAllBytes(), maxCharacters + 1);
        } catch (IOException | RuntimeException unreadable) {
            log.info("A person document was left out of an AI run: {}", unreadable.toString());
            return null;
        }
    }

    private static String labelOf(String constant) {
        String spaced = constant.replace('_', ' ').toLowerCase(Locale.ROOT);
        return constant.length() <= 2 ? constant : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max).strip() + CUT;
    }

    private static String joined(List<String> parts, String separator) {
        return parts.isEmpty() ? MaterialText.NONE_ON_FILE : String.join(separator, parts);
    }
}
