package app.lightmove.api.dataexport.service;

import app.lightmove.api.candidate.model.ArchivedDocumentFile;
import app.lightmove.api.candidate.model.ArchivedNote;
import app.lightmove.api.candidate.model.ArchivedPerson;
import app.lightmove.api.candidate.model.PoolCriteria;
import app.lightmove.api.candidate.service.CandidatePoolService;
import app.lightmove.api.candidate.service.PersonArchiveReader;
import app.lightmove.api.core.export.service.ZipArchiveWriter;
import app.lightmove.api.core.security.rbac.ProjectAudience;
import app.lightmove.api.core.text.service.CsvFormatter;
import app.lightmove.api.customcolumn.dto.CustomColumnDto;
import app.lightmove.api.customcolumn.service.CustomColumnService;
import app.lightmove.api.dataexport.model.ExportedCounts;
import app.lightmove.api.pairing.model.PairedStage;
import app.lightmove.api.pairing.service.StagePairingService;
import app.lightmove.api.position.service.PositionService;
import app.lightmove.api.project.dto.ProjectResponse;
import app.lightmove.api.project.service.ProjectService;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.model.TriageCompanyFilters;
import app.lightmove.api.workspace.constant.WorkspaceMode;
import app.lightmove.api.workspace.service.AuditLogService;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * What a whole-workspace export holds (#926), written into a zip as it is read: every position with its brief and its
 * three Companies stages as the grid exports them, everyone the workspace holds as the Candidates page exports them,
 * each person's notes and the latest file of each of their documents, and the whole audit log. It reads through the
 * seams the screens use, so a field the staff grid would not show is not in the file either.
 */
@Service
@RequiredArgsConstructor
public class WorkspaceArchiveWriter {

    /** Higher than any one screen's export cap: this is the workspace's last copy, so nothing is cut. */
    private static final int WHOLE_STAGE = 1_000_000;
    private static final int PEOPLE_PAGE = 500;
    private static final String BYTE_ORDER_MARK = "﻿";
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final ProjectService projects;
    private final PositionService positions;
    private final StagePairingService pairing;
    private final CustomColumnService customColumns;
    private final CompaniesCsvWriter companiesCsv;
    private final CandidatePoolService pool;
    private final CandidatesCsvWriter candidatesCsv;
    private final PersonArchiveReader people;
    private final AuditLogService auditLog;
    private final ObjectMapper json;

    public ExportedCounts write(UUID workspaceId, UUID requesterId, String workspaceName, WorkspaceMode mode,
                                Instant builtAt, ZipArchiveWriter zip) throws IOException {
        zip.putText("README.txt", readmeOf(workspaceName, mode, builtAt));
        int positionCount = writePositions(workspaceId, mode, zip);
        PeopleWritten written = writePeople(workspaceId, requesterId, zip);
        try (Writer log = zip.openText("audit-log.csv")) {
            auditLog.writeWholeLog(workspaceId, log);
        }
        return new ExportedCounts(written.people(), written.documents(), positionCount);
    }

    private int writePositions(UUID workspaceId, WorkspaceMode mode, ZipArchiveWriter zip) throws IOException {
        List<ProjectResponse> all = projects.listEveryInWorkspace(workspaceId);
        StringBuilder index = new StringBuilder(BYTE_ORDER_MARK).append(row(List.of("Position",
                mode == WorkspaceMode.AGENCY ? "Client" : "Business unit", "Type", "Created", "Start", "Delivery",
                "Target hire", "Archived", "Folder")));
        int number = 0;
        for (ProjectResponse project : all) {
            number++;
            String folder = "positions/%03d-%s".formatted(number,
                    ZipArchiveWriter.slugOf(project.positionTitle(), "position"));
            index.append(row(List.of(text(project.positionTitle()), text(project.clientName()),
                    project.projectType() == null ? "" : capitalised(project.projectType().name()),
                    day(project.createdAt()), day(project.startDate()), day(project.deliveryDate()),
                    day(project.targetDate()), day(project.archivedAt()), folder)));

            zip.putText(folder + "/brief.json", json.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(positions.briefOf(workspaceId, project.id())));
            List<CustomColumnDto> columns = customColumns.list(workspaceId, project.id()).columns();
            for (TriageCompanyStatus stage : TriageCompanyStatus.values()) {
                PairedStage paired = pairing.pair(workspaceId, project.id(), stage, TriageCompanyFilters.none(),
                        WHOLE_STAGE, WHOLE_STAGE, ProjectAudience.STAFF, true);
                zip.putText(folder + "/companies-" + ZipArchiveWriter.slugOf(stage.name(), "stage") + ".csv",
                        companiesCsv.write(ProjectAudience.STAFF, columns, ProjectExportService.rowsOf(paired)));
            }
        }
        zip.putText("positions.csv", index.toString());
        return all.size();
    }

    private PeopleWritten writePeople(UUID workspaceId, UUID requesterId, ZipArchiveWriter zip) throws IOException {
        List<UUID> everyone = people.personIdsOf(workspaceId);
        int documents = 0;
        try (Writer csv = zip.openText("candidates.csv")) {
            csv.write(candidatesCsv.header());
            for (int start = 0; start < everyone.size(); start += PEOPLE_PAGE) {
                List<UUID> page = everyone.subList(start, Math.min(start + PEOPLE_PAGE, everyone.size()));
                csv.write(candidatesCsv.lines(pool.exportOf(requesterId, workspaceId, PoolCriteria.everyone(), page,
                        PEOPLE_PAGE)));
            }
        }
        for (int start = 0; start < everyone.size(); start += PEOPLE_PAGE) {
            List<UUID> page = everyone.subList(start, Math.min(start + PEOPLE_PAGE, everyone.size()));
            for (ArchivedPerson person : people.archiveOf(workspaceId, page)) {
                String folder = "people/" + ZipArchiveWriter.slugOf(person.fullName(), "person") + "-"
                        + person.personId().toString().substring(0, 8);
                if (!person.notes().isEmpty()) {
                    zip.putText(folder + "/notes.md", notesOf(person));
                }
                for (ArchivedDocumentFile document : person.documents()) {
                    try (InputStream bytes = people.open(document)) {
                        zip.putStream(folder + "/documents/" + ZipArchiveWriter.slugOf(document.category(), "document")
                                + "-" + ZipArchiveWriter.fileNameOf(document.file().fileName(), "file"), bytes);
                    }
                    documents++;
                }
            }
        }
        return new PeopleWritten(everyone.size(), documents);
    }

    private static String notesOf(ArchivedPerson person) {
        StringBuilder markdown = new StringBuilder("# ").append(text(person.fullName())).append("\n");
        for (ArchivedNote note : person.notes()) {
            markdown.append("\n## ").append(capitalised(note.kind()))
                    .append(" · ").append(note.positionTitle() == null ? "All positions" : note.positionTitle())
                    .append(" · ").append(note.authorName() == null ? "Someone who has left" : note.authorName())
                    .append(" · ").append(STAMP.format(note.createdAt()));
            if (note.pinned()) {
                markdown.append(" · pinned");
            }
            if (note.editedAt() != null) {
                markdown.append(" · edited ").append(STAMP.format(note.editedAt()));
            }
            markdown.append("\n\n").append(note.body()).append("\n");
        }
        return markdown.toString();
    }

    private static String readmeOf(String workspaceName, WorkspaceMode mode, Instant builtAt) {
        String client = mode == WorkspaceMode.AGENCY ? "client" : "business unit";
        return """
                Export of %s from Uncava, built %s.

                positions.csv            every position, archived ones included, with its %s and dates
                positions/<n>-<title>/   each position's brief (brief.json) and its Companies stages
                                         (companies-in-universe.csv, companies-shortlisted.csv, companies-declined.csv)
                candidates.csv           everyone the workspace holds, as the Candidates page exports them
                people/<name>/           each person's notes (notes.md) and the latest file of each document
                audit-log.csv            the whole audit log, as Settings → Security shows it

                CSV files are UTF-8 with a byte order mark, so Excel reads accented names correctly.
                """.formatted(workspaceName, STAMP.format(builtAt), client);
    }

    private static String day(Instant instant) {
        return instant == null ? "" : DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC).format(instant);
    }

    private static String day(LocalDate date) {
        return date == null ? "" : date.toString();
    }

    private static String capitalised(String token) {
        if (token == null || token.isEmpty()) {
            return "";
        }
        String lower = token.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String row(List<String> values) {
        return CsvFormatter.row(values.stream().map(CsvFormatter::defused).toList()) + "\r\n";
    }

    private record PeopleWritten(int people, int documents) {}
}
