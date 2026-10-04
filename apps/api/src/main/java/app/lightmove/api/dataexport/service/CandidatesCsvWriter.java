package app.lightmove.api.dataexport.service;

import app.lightmove.api.candidate.model.PoolPersonExport;
import app.lightmove.api.core.text.service.CsvFormatter;
import app.lightmove.api.dataexport.constant.ExportColumn;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Writes the workspace's people as the Candidates page lists them, one person per line. */
@Service
public class CandidatesCsvWriter {

    /** Without a BOM Excel on Windows reads ANSI and mangles non-ASCII names; our importer strips it. */
    private static final String BYTE_ORDER_MARK = "﻿";
    private static final String LIST_SEPARATOR = "; ";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);
    private static final List<String> HEADERS = List.of("Name", "Title", "Company", "City", "Country", "LinkedIn",
            "Emails", "Phones", "Positions", "Tags", "Owner", "Do not contact", "Added");

    public String write(List<PoolPersonExport> people) {
        StringBuilder csv = new StringBuilder(BYTE_ORDER_MARK).append(row(HEADERS));
        for (PoolPersonExport person : people) {
            csv.append(row(List.of(
                    text(person.fullName()), text(person.title()), text(person.companyName()),
                    text(person.locationCity()), text(person.locationCountry()), text(person.linkedinUrl()),
                    String.join(LIST_SEPARATOR, person.emails()), String.join(LIST_SEPARATOR, person.phones()),
                    person.positions().stream()
                            .map(position -> text(position.positionTitle()) + " (" + ExportColumn.statusLabel(
                                    position.status()) + ")")
                            .collect(Collectors.joining(LIST_SEPARATOR)),
                    String.join(LIST_SEPARATOR, person.tags()), text(person.ownerName()),
                    person.doNotContact() ? "Yes" : "",
                    person.addedAt() == null ? "" : DAY.format(person.addedAt()))));
        }
        return csv.toString();
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String row(List<String> values) {
        return CsvFormatter.row(values.stream().map(CsvFormatter::defused).toList()) + "\r\n";
    }
}
