package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.model.CandidateMaterial;
import app.lightmove.api.candidate.model.DocumentOnFile;
import app.lightmove.api.candidate.model.NoteOnFile;
import app.lightmove.api.candidate.service.CandidateMaterialService;
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
 * Writes out what the team has filed on a person for a prompt: every note, newest first, and the text of the latest
 * file of every document a text can be read from — an image or a scan without a text layer is left out. Emails and
 * phone numbers are taken out of both, and each is held to a length, so a long file never crowds out the rest.
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

    private static final Set<String> READABLE = Stream.of(DocumentFormat.PDF, DocumentFormat.DOCX, DocumentFormat.DOC,
            DocumentFormat.TXT).map(DocumentFormat::contentType).collect(Collectors.toSet());
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private final CandidateMaterialService material;
    private final PositionDocumentTextReader textReader;

    MaterialText read(UUID projectId, UUID candidateId) {
        CandidateMaterial filed = material.materialOf(projectId, candidateId);
        List<String> documents = documentsOf(filed.documents());
        return new MaterialText(notesOf(filed.notes()), joined(documents, "\n\n"), filed.notes().size(),
                documents.size());
    }

    private static String notesOf(List<NoteOnFile> notes) {
        List<String> lines = new ArrayList<>();
        int total = 0;
        for (NoteOnFile note : notes) {
            String line = "- [%s · %s%s] %s".formatted(labelOf(note.kind().name()), DAY.format(note.writtenAt()),
                    note.aboutPosition() == null ? "" : " · about " + note.aboutPosition(),
                    cut(ContactRedaction.redact(note.body().strip()), MAX_NOTE));
            if (total + line.length() > MAX_NOTES) {
                lines.add("- older notes left out");
                break;
            }
            lines.add(line);
            total += line.length();
        }
        return joined(lines, "\n");
    }

    private List<String> documentsOf(List<DocumentOnFile> documents) {
        List<String> read = new ArrayList<>();
        int total = 0;
        for (DocumentOnFile document : documents) {
            if (!READABLE.contains(document.file().contentType())) {
                continue;
            }
            String text = textOf(document);
            if (text == null) {
                continue;
            }
            String block = "=== %s: %s ===\n%s".formatted(labelOf(document.category().name()), document.title(),
                    cut(ContactRedaction.redact(text), MAX_DOCUMENT));
            if (total + block.length() > MAX_DOCUMENTS) {
                break;
            }
            read.add(block);
            total += block.length();
        }
        return read;
    }

    /** A file that will not open or read is left out, never the run: the rest of what is on file still counts. */
    private String textOf(DocumentOnFile document) {
        try (InputStream in = material.open(document.file())) {
            return textReader.read(in.readAllBytes());
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
