package app.lightmove.api.dataexport.service;

import app.lightmove.api.core.text.service.CsvFormatter;
import app.lightmove.api.customcolumn.constant.CustomColumnTarget;
import app.lightmove.api.customcolumn.dto.CustomColumnDto;
import app.lightmove.api.dataexport.constant.ExportColumn;
import app.lightmove.api.dataexport.model.ExportRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Writes the Companies grid as a CSV: built-in columns in grid order, then the mandate's custom columns. */
@Service
public class CompaniesCsvWriter {

    /** Without a BOM Excel on Windows reads ANSI and mangles non-ASCII names; our importer strips it. */
    private static final String BYTE_ORDER_MARK = "﻿";

    public String write(List<CustomColumnDto> customColumns, List<ExportRow> rows) {
        List<ExportColumn> builtIns = ExportColumn.all();
        List<CustomColumnDto> extras = customColumns.stream().filter(column -> !column.hidden()).toList();

        StringBuilder csv = new StringBuilder(BYTE_ORDER_MARK);
        csv.append(row(headers(builtIns, extras)));
        for (ExportRow line : rows) {
            csv.append(row(cells(builtIns, extras, line)));
        }
        return csv.toString();
    }

    private static List<String> headers(List<ExportColumn> builtIns, List<CustomColumnDto> extras) {
        List<String> headers = new ArrayList<>(builtIns.stream().map(ExportColumn::header).toList());
        extras.forEach(column -> headers.add(column.label()));
        return headers;
    }

    private static List<String> cells(List<ExportColumn> builtIns, List<CustomColumnDto> extras,
                                      ExportRow line) {
        List<String> cells = new ArrayList<>(builtIns.stream().map(column -> column.valueOf(line)).toList());
        extras.forEach(column -> cells.add(customValueOf(line, column)));
        return cells;
    }

    /** A company column repeats on every one of its people's lines, as the company name does. */
    private static String customValueOf(ExportRow line, CustomColumnDto column) {
        Map<String, String> values = column.target().equals(CustomColumnTarget.COMPANY.value())
                ? (line.company() == null ? null : line.company().customFields())
                : (line.candidate() == null ? null : line.candidate().customFields());
        if (values == null) {
            return "";
        }
        String value = values.get(column.fieldKey());
        return value == null ? "" : value;
    }

    private static String row(List<String> values) {
        List<String> defused = values.stream().map(value -> CsvFormatter.defused(value == null ? "" : value)).toList();
        return CsvFormatter.row(defused) + "\r\n";
    }
}
