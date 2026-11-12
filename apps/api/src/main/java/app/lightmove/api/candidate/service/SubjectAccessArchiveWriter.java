package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.AiUse;
import app.lightmove.api.candidate.model.SubjectAccessArchive;
import app.lightmove.api.candidate.model.SubjectAccessFile;
import app.lightmove.api.candidate.model.SubjectAccessSection;
import app.lightmove.api.core.export.service.ZipArchiveWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes a subject access export as its zip: a README for whoever sends it, {@code person.json} for the person to
 * take elsewhere (Art. 20), {@code person.html} to read, and the files. Touches no row: the documents' bytes come
 * from the bucket, and everything else was read before the response began.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class SubjectAccessArchiveWriter {

    /** Names the shape of {@code person.json}, so a reader can tell one revision of it from the next. */
    static final String FORMAT = "uncava-subject-access/1";

    static final String UNREADABLE_LIST = "documents/NOT-INCLUDED.txt";

    private final PersonDocumentService documents;
    private final ObjectMapper json;

    void write(SubjectAccessArchive archive, OutputStream target) throws IOException {
        try (ZipArchiveWriter zip = new ZipArchiveWriter(target)) {
            zip.putText("README.txt", readmeOf(archive));
            zip.putText("person.json", json.writerWithDefaultPrettyPrinter().writeValueAsString(documentOf(archive)));
            zip.putText("person.html", SubjectAccessHtml.render(archive));
            List<SubjectAccessFile> unreadable = new ArrayList<>();
            for (SubjectAccessFile file : archive.files()) {
                byte[] content = file.content() != null ? file.content() : storedContentOf(file);
                if (content == null) {
                    unreadable.add(file);
                } else {
                    zip.putStream(file.path(), new ByteArrayInputStream(content));
                }
            }
            if (!unreadable.isEmpty()) {
                zip.putText(UNREADABLE_LIST, unreadableListOf(unreadable));
            }
        }
    }

    /**
     * A request has a legal deadline, so a file the bucket cannot give back is left out and named rather than failing
     * the rest. Read whole first (a file is at most 20 MB), so a read failing midway never leaves a cut-off copy in the
     * zip under the document's name; a failure writing to the response still ends the download.
     */
    private byte @Nullable [] storedContentOf(SubjectAccessFile file) {
        if (file.stored() == null) {
            return null;
        }
        try (InputStream content = documents.open(file.stored())) {
            return content.readAllBytes();
        } catch (IOException | RuntimeException failure) {
            log.warn("subject access export left out document version {}: its file could not be read",
                    file.versionId(), failure);
            return null;
        }
    }

    private static String unreadableListOf(List<SubjectAccessFile> unreadable) {
        StringBuilder text = new StringBuilder("""
                These documents are on the record, but their files could not be read when this export was made.
                Everything else is in the export. Export again later, or send these files separately.

                """);
        for (SubjectAccessFile file : unreadable) {
            text.append("- ").append(file.stored() == null ? file.path() : file.stored().fileName())
                    .append(" (document version ").append(file.versionId()).append(")\n");
        }
        return text.toString();
    }

    static Map<String, Object> documentOf(SubjectAccessArchive archive) {
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("format", FORMAT);
        export.put("person", archive.personName());
        export.put("heldBy", archive.workspaceName());
        export.put("exportedBy", archive.exportedBy());
        export.put("exportedAt", archive.exportedAt().toString());
        Map<String, Object> sections = new LinkedHashMap<>();
        for (SubjectAccessSection section : archive.sections()) {
            sections.put(section.key(), section.rows());
        }
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("export", export);
        document.put("aboutThisData", archive.about());
        document.put("aiLogic", aiLogicOf(archive.aiUses()));
        document.put("data", sections);
        document.put("references", archive.references());
        return document;
    }

    private static List<Map<String, String>> aiLogicOf(List<AiUse> uses) {
        return uses.stream().map(use -> {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("use", use.name());
            entry.put("title", use.title());
            entry.put("logic", use.logic());
            return entry;
        }).toList();
    }

    private static String readmeOf(SubjectAccessArchive archive) {
        return """
                Personal data held on %s
                Held by %s. Exported from Uncava by %s on %s.

                person.html      Everything held on them, to read in a browser.
                person.json      The same data, machine-readable, to take to another service (GDPR Art. 20).
                documents/       The latest file of each document on their record.
                documents/NOT-INCLUDED.txt
                                 Only where a document's file could not be read: the ones left out.
                photo.*          The photo on their record, where there is one.
                earlier-copies/  A copy of their photo kept from before their record moved to one place.

                Before you send it
                - It holds everything on the person, the team's own notes and assessments included. Send it only to
                  them, through a channel you trust.
                - Notes, emails and call transcripts are given as written and may mention other people. Remove their
                  details where the request does not need them.
                - Colleagues are named where they wrote or did something on the record; that can stay.
                """.formatted(archive.personName(), archive.workspaceName(), archive.exportedBy(),
                archive.exportedAt().toString().substring(0, 10));
    }
}
