package app.lightmove.api.workspace.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.workspace.constant.AuditActorKind;
import app.lightmove.api.workspace.dto.AuditLogEntryResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditLogCsvWriterTest {

    @Test
    @DisplayName("every cell is defused, so a name or title a spreadsheet would run as a formula stays text")
    void defusesFormulae() {
        AuditLogEntryResponse entry = new AuditLogEntryResponse(1L, Instant.parse("2026-10-01T09:30:00Z"),
                "PROJECT_TEAM_CHANGED", "membersAndAccess", "FAILURE", AuditActorKind.MEMBER, UUID.randomUUID(),
                "=HYPERLINK(\"http://evil.example\")", null, true, "Added someone to the position",
                "+cmd|' /C calc'!A0", "10.0.0.1");

        String csv = new AuditLogCsvWriter().write(List.of(entry));

        assertThat(csv).contains("2026-10-01T09:30:00Z")
                .contains("'=HYPERLINK")
                .contains("(removed)")
                .contains("'+cmd")
                .contains("Members & access")
                .contains("Failed")
                .endsWith("\r\n");
    }
}
