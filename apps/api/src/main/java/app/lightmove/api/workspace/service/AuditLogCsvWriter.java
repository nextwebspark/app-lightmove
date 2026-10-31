package app.lightmove.api.workspace.service;

import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.text.service.CsvFormatter;
import app.lightmove.api.workspace.constant.AuditLogGroup;
import app.lightmove.api.workspace.dto.AuditLogEntryResponse;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;

/** Writes the audit log as the screen shows it, one event per line, every cell defused against formula injection. */
@Service
public class AuditLogCsvWriter {

    /** Without a BOM Excel on Windows reads ANSI and mangles non-ASCII names. */
    private static final String BYTE_ORDER_MARK = "﻿";
    private static final List<String> HEADERS = List.of("Time (UTC)", "Person", "Event", "Category", "Outcome",
            "Position", "IP address");

    public String write(List<AuditLogEntryResponse> entries) {
        StringBuilder csv = new StringBuilder(BYTE_ORDER_MARK).append(row(HEADERS));
        for (AuditLogEntryResponse entry : entries) {
            csv.append(row(List.of(
                    DateTimeFormatter.ISO_INSTANT.format(entry.occurredAt()),
                    text(entry.actorName()) + (entry.actorRemoved() ? " (removed)" : ""),
                    text(entry.summary()),
                    categoryOf(entry.group()),
                    "FAILURE".equals(entry.outcome()) ? "Failed" : "Succeeded",
                    text(entry.positionTitle()),
                    text(entry.ipAddress()))));
        }
        return csv.toString();
    }

    private static String categoryOf(String group) {
        AuditLogGroup resolved = ApiValueEnum.fromValue(AuditLogGroup.class, group);
        return resolved == null ? "" : resolved.label();
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String row(List<String> values) {
        return CsvFormatter.row(values.stream().map(CsvFormatter::defused).toList()) + "\r\n";
    }
}
