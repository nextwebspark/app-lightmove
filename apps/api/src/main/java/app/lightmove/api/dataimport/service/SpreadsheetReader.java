package app.lightmove.api.dataimport.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.SpreadsheetImportSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
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
 * Turns an uploaded CSV or Excel file into a {@link ParsedSheet}.
 *
 * <p><b>The file's declared content type decides nothing.</b> Browsers disagree about what a
 * {@code .csv} is and several send {@code application/vnd.ms-excel} for one, so the format is decided
 * by the bytes: a workbook starts with a recognisable signature, and anything else is read as
 * delimited text. The allowlist in {@link SpreadsheetImportSettings} only keeps obviously wrong
 * uploads out before any of this runs.
 *
 * <p>Only the first sheet is read. A workbook with several is a workbook whose author knows which one
 * they mean, and importing all of them would silently merge tables that do not share a header row.
 */
@Service
public class SpreadsheetReader {

    /** ZIP local-file header — every .xlsx is a zip, and .xls is the older OLE2 compound file. */
    private static final byte[] XLSX_SIGNATURE = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] XLS_SIGNATURE =
            {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

    private static final char[] CANDIDATE_DELIMITERS = {',', ';', '\t', '|'};
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    /** Enough rows to judge a column's shape without holding the whole file to do it. */
    private static final int SHAPE_SAMPLE_ROWS = 50;
    /** How many distinct values the mapping step shows back to the person confirming it. */
    private static final int SAMPLE_VALUES = 3;

    private final SpreadsheetImportSettings settings;

    // Hand-written rather than @RequiredArgsConstructor: it derives the settings branch from the
    // properties root rather than taking it, which is the one case the Lombok rule exempts.
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
            throw new ApiException(ErrorCode.FILE_TOO_LARGE,
                    "upload of " + file.getSize() + " bytes exceeds " + settings.maxFileSizeBytes());
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
                // getLastCellNum, not the row's iterator: the iterator skips cells that were never
                // written, which would shift every value after a blank one into the wrong column.
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
     * Reads a cell as the text a person looking at the sheet would see.
     *
     * <p>A formula is read as its <b>cached result</b>, never evaluated: evaluating means running
     * arbitrary spreadsheet logic — including external links and volatile functions — out of a file an
     * untrusted caller uploaded, and the value a consultant saw when they saved is the value they
     * meant to send.
     *
     * <p>A whole number comes back without the {@code .0} Excel's double-typed cells would otherwise
     * add, because "500" and "500.0" are the same headcount and only one of them looks like data.
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

    /** A UTF-8 BOM would otherwise become part of the first header, so the first column matches nothing. */
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
     * Which character separates the columns, judged from the header line.
     *
     * <p>Necessary rather than fussy: Excel's "Save as CSV" writes semicolons in every locale that
     * uses a decimal comma, which is most of Europe and much of the Gulf, and a file read with the
     * wrong delimiter parses as one very wide column and imports nothing. Whichever candidate appears
     * most often in the header wins; a tie falls to the comma.
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

    /**
     * Turns the raw table into the sheet the rest of the import works from: the first non-empty row is
     * the header, every later row is data padded to the header's width, and each column is profiled.
     */
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
                padded.add(value == null ? "" : value.trim());
            }
            rows.add(padded);
            if (rows.size() > settings.maxRows()) {
                throw new ApiException(ErrorCode.IMPORT_TOO_MANY_ROWS,
                        "file carries more than " + settings.maxRows() + " data rows");
            }
        }

        List<SheetColumn> columns = new ArrayList<>(headers.size());
        for (int index = 0; index < headers.size(); index++) {
            columns.add(profile(index, headers.get(index), rows));
        }
        return new ParsedSheet(columns, rows);
    }

    /**
     * Gives every column a usable, distinct header.
     *
     * <p>A blank header becomes {@code Column 4} rather than being dropped: the cells under it are
     * still data, and dropping the column would shift every column after it. A repeated header gets a
     * numeric suffix, because two columns the mapping step cannot tell apart is two columns a person
     * cannot map — and exports do repeat them ("Email", "Email").
     */
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
