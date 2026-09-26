package app.lightmove.api.dataimport.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.SpreadsheetImportSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.text.service.CsvFormatter;
import app.lightmove.api.dataimport.model.ParsedSheet;
import app.lightmove.api.dataimport.model.SheetColumn;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Turns an uploaded CSV or Excel file into a {@link ParsedSheet}. The bytes decide the format, never
 * the declared content type (browsers disagree about {@code .csv}); {@link SpreadsheetImportSettings}'
 * allowlist only screens out the obviously wrong. Only the first sheet is read.
 */
@Service
public class SpreadsheetReader {

    /** ZIP local-file header — every .xlsx is a zip, and .xls is the older OLE2 compound file. */
    private static final byte[] XLSX_SIGNATURE = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] XLS_SIGNATURE =
            {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

    private static final char[] CANDIDATE_DELIMITERS = {',', ';', '\t', '|'};
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    /** Enough rows to judge a column's shape. */
    private static final int SHAPE_SAMPLE_ROWS = 50;
    /** Distinct values the mapping step shows back. */
    private static final int SAMPLE_VALUES = 3;

    private final SpreadsheetImportSettings settings;

    public SpreadsheetReader(LightMoveProperties properties) {
        this.settings = properties.spreadsheetImport();
    }

    public ParsedSheet read(MultipartFile file) {
        byte[] content = contentOf(file);
        List<List<String>> table = isWorkbook(content) ? readWorkbook(content) : readDelimited(content);
        return toSheet(table);
    }

    private byte[] contentOf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED, "Choose a file to import");
        }
        if (file.getSize() > settings.maxFileSizeBytes()) {
            // The ceiling is configuration, not request input, so it is safe to name in the message.
            throw ApiException.userFacing(ErrorCode.FILE_TOO_LARGE,
                    "That file is larger than the " + megabytes(settings.maxFileSizeBytes())
                            + " MB an import can take.");
        }
        if (!settings.allows(file.getContentType())) {
            throw new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "rejected content type " + file.getContentType());
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded spreadsheet", e);
        }
    }

    /** Whole megabytes, rounded down. */
    private static long megabytes(long bytes) {
        return bytes / (1024 * 1024);
    }

    private static boolean isWorkbook(byte[] content) {
        return startsWith(content, XLSX_SIGNATURE) || startsWith(content, XLS_SIGNATURE);
    }

    private static boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (content[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private List<List<String>> readWorkbook(byte[] content) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw ApiException.of(ErrorCode.IMPORT_FILE_UNREADABLE);
            }
            Sheet sheet = workbook.getSheetAt(0);
            List<List<String>> table = new ArrayList<>();
            for (Row row : sheet) {
                List<String> values = new ArrayList<>();
                // Not the row's iterator: it skips never-written cells, shifting later values left.
                for (int column = 0; column < Math.max(row.getLastCellNum(), 0); column++) {
                    values.add(stringValueOf(row.getCell(column)));
                }
                table.add(values);
            }
            return table;
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException apiException) {
                throw apiException;
            }
            throw new ApiException(ErrorCode.IMPORT_FILE_UNREADABLE,
                    "workbook could not be opened: " + e.getMessage());
        }
    }

    /**
     * A formula is read as its <b>cached result</b>, never evaluated: that would run arbitrary logic,
     * external links included, out of an untrusted upload.
     */
    private static String stringValueOf(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType()
                : cell.getCellType();
        return switch (type) {
            case STRING -> cell.getStringCellValue().trim();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().format(ISO_DATE);
                }
                yield BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            }
            default -> "";
        };
    }

    private List<List<String>> readDelimited(byte[] content) {
        String text = new String(stripByteOrderMark(content), StandardCharsets.UTF_8);
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(sniffDelimiter(text))
                .setIgnoreSurroundingSpaces(true)
                .setIgnoreEmptyLines(true)
                .get();

        try (Reader reader = new InputStreamReader(
                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(reader, format)) {
            List<List<String>> table = new ArrayList<>();
            for (CSVRecord record : parser) {
                List<String> values = new ArrayList<>(record.size());
                record.forEach(values::add);
                table.add(values);
            }
            return table;
        } catch (IOException | IllegalStateException | IllegalArgumentException e) {
            throw new ApiException(ErrorCode.IMPORT_FILE_UNREADABLE,
                    "delimited file could not be parsed: " + e.getMessage());
        }
    }

    /**
     * Drops the formula-guard apostrophe Excel and {@code CompaniesCsvWriter} write, so {@code +966 …}
     * round-trips — only before a formula character; elsewhere an apostrophe is data.
     */
    private static String unescapedFormulaGuard(String value) {
        return value.length() > 1 && value.charAt(0) == '\''
                && CsvFormatter.FORMULA_STARTERS.indexOf(value.charAt(1)) >= 0
                ? value.substring(1)
                : value;
    }

    /** A UTF-8 BOM would otherwise make the first header match nothing. */
    private static byte[] stripByteOrderMark(byte[] content) {
        if (content.length >= 3
                && (content[0] & 0xFF) == 0xEF && (content[1] & 0xFF) == 0xBB && (content[2] & 0xFF) == 0xBF) {
            byte[] withoutMark = new byte[content.length - 3];
            System.arraycopy(content, 3, withoutMark, 0, withoutMark.length);
            return withoutMark;
        }
        return content;
    }

    /**
     * Judged from the header line: Excel writes semicolons wherever the decimal mark is a comma. The
     * most frequent candidate wins; a tie falls to the comma.
     */
    private static char sniffDelimiter(String text) {
        int lineEnd = text.indexOf('\n');
        String header = lineEnd < 0 ? text : text.substring(0, lineEnd);

        char best = ',';
        int bestCount = 0;
        for (char candidate : CANDIDATE_DELIMITERS) {
            int count = 0;
            for (int i = 0; i < header.length(); i++) {
                if (header.charAt(i) == candidate) {
                    count++;
                }
            }
            if (count > bestCount) {
                best = candidate;
                bestCount = count;
            }
        }
        return best;
    }

    /** The first non-empty row is the header; later rows are padded to its width. */
    private ParsedSheet toSheet(List<List<String>> table) {
        int headerIndex = -1;
        for (int i = 0; i < table.size(); i++) {
            if (table.get(i).stream().anyMatch(value -> value != null && !value.isBlank())) {
                headerIndex = i;
                break;
            }
        }
        if (headerIndex < 0) {
            throw ApiException.of(ErrorCode.IMPORT_FILE_UNREADABLE);
        }

        List<String> headers = namedHeaders(table.get(headerIndex));
        List<List<String>> rows = new ArrayList<>();
        for (int i = headerIndex + 1; i < table.size(); i++) {
            List<String> source = table.get(i);
            if (source.stream().allMatch(value -> value == null || value.isBlank())) {
                continue;
            }
            List<String> padded = new ArrayList<>(headers.size());
            for (int column = 0; column < headers.size(); column++) {
                String value = column < source.size() ? source.get(column) : null;
                padded.add(value == null ? "" : unescapedFormulaGuard(value.trim()));
            }
            rows.add(padded);
            if (rows.size() > settings.maxRows()) {
                throw ApiException.userFacing(ErrorCode.IMPORT_TOO_MANY_ROWS,
                        "That file has more than " + settings.maxRows()
                                + " rows. Split it and import the parts.");
            }
        }

        List<SheetColumn> columns = new ArrayList<>(headers.size());
        for (int index = 0; index < headers.size(); index++) {
            columns.add(profile(index, headers.get(index), rows));
        }
        return new ParsedSheet(columns, rows);
    }

    /** A blank header becomes {@code Column 4}, never dropped; a repeated one gets a numeric suffix. */
    private static List<String> namedHeaders(List<String> rawHeaders) {
        List<String> headers = new ArrayList<>(rawHeaders.size());
        Set<String> taken = new HashSet<>();
        for (int index = 0; index < rawHeaders.size(); index++) {
            String raw = rawHeaders.get(index);
            String header = raw == null || raw.isBlank() ? "Column " + (index + 1) : raw.trim();
            String unique = header;
            for (int suffix = 2; !taken.add(unique.toLowerCase(Locale.ROOT)); suffix++) {
                unique = header + " " + suffix;
            }
            headers.add(unique);
        }
        return headers;
    }

    private static SheetColumn profile(int index, String header, List<List<String>> rows) {
        List<String> samples = new ArrayList<>();
        List<String> shapeSample = new ArrayList<>();
        for (List<String> row : rows) {
            String value = index < row.size() ? row.get(index) : "";
            if (value == null || value.isBlank()) {
                continue;
            }
            if (shapeSample.size() < SHAPE_SAMPLE_ROWS) {
                shapeSample.add(value.trim());
            }
            if (samples.size() < SAMPLE_VALUES && !samples.contains(value.trim())) {
                samples.add(value.trim());
            }
            if (shapeSample.size() >= SHAPE_SAMPLE_ROWS && samples.size() >= SAMPLE_VALUES) {
                break;
            }
        }
        return new SheetColumn(index, header, ValueShapes.of(shapeSample), samples, shapeSample.isEmpty());
    }
}
