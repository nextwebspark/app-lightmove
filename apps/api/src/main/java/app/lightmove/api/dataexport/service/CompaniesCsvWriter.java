package app.lightmove.api.dataexport.service;

import app.lightmove.api.customcolumn.constant.CustomColumnTarget;
import app.lightmove.api.customcolumn.dto.CustomColumnDto;
import app.lightmove.api.dataexport.constant.ExportColumn;
import app.lightmove.api.dataexport.model.ExportRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Writes the Companies grid as a CSV: the built-in columns in the order the grid draws them, then
 * the mandate's own custom columns, then one line per person at a company.
 *
 * <p>Hand-rolled rather than {@code commons-csv}, matching {@code ImportTemplateWriter}: the same
 * dozen lines, and one idiom for a generated CSV rather than two.
 */
@Service
public class CompaniesCsvWriter {

    /**
     * Excel and Sheets evaluate a cell opening with one of these as a formula, so a company note
     * reading {@code =HYPERLINK("http://…")} would run on the machine of whoever opened the file. A
     * leading apostrophe makes the cell text, and it is also what stops a phone number beginning
     * {@code +966} from being read as arithmetic and shown as {@code #NAME?}.
     */
    private static final String FORMULA_STARTERS = "=+-@\t\r";

    /**
     * Excel on Windows reads a CSV in the machine's ANSI codepage unless a BOM says otherwise, which
     * turns every non-ASCII name in a mandate into mojibake. Safe for the round trip back through our
     * own importer: {@code SpreadsheetReader} strips a leading BOM, and its tests pin that.
     */
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

    /**
     * A company column repeats down the company's people, as the company's own name does — the fact
     * is about the employer, and blanking it on the second row would read as missing data.
     */
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

    /**
     * CRLF and RFC 4180 quoting: this file is opened in Excel far more often than by a parser, and a
     * value carrying a comma would otherwise be two columns the moment it is saved and sent back.
     */
    private static String row(List<String> values) {
        return String.join(",", values.stream().map(CompaniesCsvWriter::quoted).toList()) + "\r\n";
    }

    private static String quoted(String value) {
        String safe = defused(value == null ? "" : value);
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return '"' + safe.replace("\"", "\"\"") + '"';
        }
        return safe;
    }

    private static String defused(String value) {
        return !value.isEmpty() && FORMULA_STARTERS.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    }
}
