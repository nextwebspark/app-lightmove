package app.lightmove.api.dataexport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.email.service.EmailSender;
import app.lightmove.api.core.email.service.EmailTemplates;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.storage.service.DocumentStore;
import app.lightmove.api.core.storage.service.StorageDeletionQueue;
import app.lightmove.api.dataexport.model.ExportedCounts;
import app.lightmove.api.dataexport.model.WorkspaceExport;
import app.lightmove.api.dataexport.repository.WorkspaceExportRepository;
import app.lightmove.api.workspace.service.WorkspaceSettingsService;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

class WorkspaceExportServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-10T09:00:00Z");

    private final WorkspaceExportRepository exports = mock(WorkspaceExportRepository.class);
    private final WorkspaceArchiveWriter archive = mock(WorkspaceArchiveWriter.class);
    private final WorkspaceSettingsService workspaces = mock(WorkspaceSettingsService.class, RETURNS_DEEP_STUBS);
    private final UserRepository users = mock(UserRepository.class);
    private final DocumentStore store = mock(DocumentStore.class);
    private final StorageDeletionQueue deletions = mock(StorageDeletionQueue.class);
    private final EmailSender emails = mock(EmailSender.class);
    private final LightMoveProperties properties = mock(LightMoveProperties.class, RETURNS_DEEP_STUBS);

    private WorkspaceExportService service;
    private WorkspaceExport running;

    @BeforeEach
    void setUp() throws Exception {
        when(properties.retention().workspaceExportsAfter()).thenReturn(Duration.ofDays(7));
        when(properties.retention().workspaceExportDeadline()).thenReturn(Duration.ofHours(2));
        when(properties.web().baseUrl()).thenReturn("https://app.example");
        when(workspaces.get(any()).workspace().getName()).thenReturn("Exported Firm");
        when(store.create(anyString(), anyString())).thenReturn(new ByteArrayOutputStream());
        when(archive.write(any(), any(), any(), any(), any(), any())).thenReturn(new ExportedCounts(1, 1, 1));
        service = new WorkspaceExportService(exports, archive, workspaces, users, store, deletions,
                mock(AuditService.class), mock(ApplicationEventPublisher.class), emails, mock(EmailTemplates.class),
                mock(PlatformTransactionManager.class), properties, Clock.fixed(NOW, ZoneOffset.UTC));
        running = WorkspaceExport.start(UUID.randomUUID(), UUID.randomUUID(), "firm-export.zip",
                "ws/firm/exports/file.zip", NOW);
    }

    @Test
    @DisplayName("a build whose row was purged while it ran queues its file for deletion rather than leaving it")
    void rowPurgedUnderTheBuild() {
        UUID exportId = UUID.randomUUID();
        when(exports.findById(exportId)).thenReturn(Optional.of(running), Optional.empty());

        service.build(exportId);

        verify(deletions).enqueue(List.of("ws/firm/exports/file.zip"));
        verify(emails, never()).send(any());
    }

    @Test
    @DisplayName("a build already failed as stale is never turned READY again on a file queued for deletion")
    void rowFailedAsStaleUnderTheBuild() {
        UUID exportId = UUID.randomUUID();
        WorkspaceExport failed = WorkspaceExport.start(running.getWorkspaceId(), running.getRequestedBy(),
                "firm-export.zip", "ws/firm/exports/file.zip", NOW);
        failed.fail(NOW);
        when(exports.findById(exportId)).thenReturn(Optional.of(running), Optional.of(failed));

        service.build(exportId);

        assertThat(failed.isRunning()).isFalse();
        assertThat(failed.isDownloadableAt(NOW)).isFalse();
        verify(deletions).enqueue(eq(List.of("ws/firm/exports/file.zip")));
        verify(emails, never()).send(any());
    }
}
