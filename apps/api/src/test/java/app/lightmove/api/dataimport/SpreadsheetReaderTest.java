package app.lightmove.api.dataimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.SpreadsheetImportSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.dataimport.model.ParsedSheet;
import app.lightmove.api.dataimport.model.SheetColumn;
import app.lightmove.api.dataimport.service.SpreadsheetReader;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Reading the files consultants actually send. Every case here is a shape that broke a naive reader:
 * a BOM, a semicolon delimiter, quoted commas, a repeated header, a numeric cell Excel decided to
 * store as a double.
 */
class SpreadsheetReaderTest {

    private final SpreadsheetReader reader = new SpreadsheetReader(propertiesWith(5000));

    @Test
    @DisplayName("reads a plain CSV into a header row and data rows")
    void readsPlainCsv() {
        ParsedSheet sheet = reader.read(csv("""
                Company,Name,Email
                ACWA Power,Layla Haddad,layla@acwa.example
                Agthia Group,Omar Nasser,omar@agthia.example
                """));

        assertThat(sheet.columns()).extracting(SheetColumn::header)
                .containsExactly("Company", "Name", "Email");
        assertThat(sheet.rowCount()).isEqualTo(2);
        assertThat(sheet.rows().getFirst()).containsExactly("ACWA Power", "Layla Haddad", "layla@acwa.example");
    }

    @Test
    @DisplayName("a UTF-8 BOM does not become part of the first header")
    void stripsByteOrderMark() {
        // Excel writes one on every "CSV UTF-8" save. Left in place it makes the first header
        // "﻿Company", which matches no synonym and no existing custom column — so the one column
        // every import needs is the one that silently fails to map.
        byte[] withMark = concat(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                "Company,Name\nACWA Power,Layla Haddad\n".getBytes(StandardCharsets.UTF_8));

        ParsedSheet sheet = reader.read(new MockMultipartFile("file", "list.csv", "text/csv", withMark));

        assertThat(sheet.columns().getFirst().header()).isEqualTo("Company");
    }

    @Test
    @DisplayName("sniffs a semicolon delimiter rather than reading the file as one wide column")
    void sniffsSemicolonDelimiter() {
        // What Excel's "Save as CSV" produces in every locale with a decimal comma.
        ParsedSheet sheet = reader.read(csv("""
                Company;Name;Employees
                ACWA Power;Layla Haddad;3000
                """));

        assertThat(sheet.columns()).extracting(SheetColumn::header)
                .containsExactly("Company", "Name", "Employees");
        assertThat(sheet.rows().getFirst()).containsExactly("ACWA Power", "Layla Haddad", "3000");
    }

    @Test
    @DisplayName("keeps a quoted field's commas and newlines inside the cell")
    void keepsQuotedFieldsWhole() {
        ParsedSheet sheet = reader.read(csv("""
                Company,Note
                ACWA Power,"Riyadh, Saudi Arabia"
                """));

        assertThat(sheet.rows().getFirst().get(1)).isEqualTo("Riyadh, Saudi Arabia");
    }

    @Test
    @DisplayName("names a blank header and suffixes a repeated one")
    void namesEveryColumnDistinctly() {
        // Exports do repeat a header, and a blank one still has data under it — dropping the column
        // would shift every column after it onto the wrong field.
        ParsedSheet sheet = reader.read(csv("""
                Email,,Email
                a@example.com,x,b@example.com
                """));

        assertThat(sheet.columns()).extracting(SheetColumn::header)
                .containsExactly("Email", "Column 2", "Email 2");
    }

    @Test
    @DisplayName("skips blank rows and pads short ones to the header's width")
    void normalisesRowShape() {
        ParsedSheet sheet = reader.read(csv("""
                Company,Name,Email
                ACWA Power,Layla Haddad

                Agthia Group
                """));

        assertThat(sheet.rowCount()).isEqualTo(2);
        assertThat(sheet.rows()).allSatisfy(row -> assertThat(row).hasSize(3));
        assertThat(sheet.cell(sheet.rows().get(1), 2)).isNull();
    }

    @Test
    @DisplayName("reads an xlsx, including a numeric cell, without a trailing .0")
    void readsWorkbook() throws Exception {
        byte[] workbook = workbook(
                List.of("Company", "Employees"),
                List.of(List.of("ACWA Power", "3000")));

        ParsedSheet sheet = reader.read(new MockMultipartFile("file", "list.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook));

        assertThat(sheet.columns()).extracting(SheetColumn::header).containsExactly("Company", "Employees");
        // The cell is a double in the file. "3000.0" is the same headcount and does not look like data.
        assertThat(sheet.rows().getFirst()).containsExactly("ACWA Power", "3000");
    }

    @Test
    @DisplayName("a workbook is recognised by its bytes, whatever content type the browser claimed")
    void decidesFormatFromTheBytes() throws Exception {
        // Browsers send application/vnd.ms-excel for a .csv and octet-stream for an .xlsx. Trusting
        // the claim would read a real workbook as text and import one row of XML.
        byte[] workbook = workbook(List.of("Company"), List.of(List.of("ACWA Power")));

        ParsedSheet sheet = reader.read(new MockMultipartFile("file", "list.csv", "text/csv", workbook));

        assertThat(sheet.columns()).extracting(SheetColumn::header).containsExactly("Company");
    }

    @Test
    @DisplayName("profiles each column's value shape from its own cells")
    void profilesValueShapes() {
        ParsedSheet sheet = reader.read(csv("""
                Contact,Headcount,Started
                layla@acwa.example,3000,2019-04-01
                omar@agthia.example,1200,2020-11-15
                """));

        assertThat(sheet.columns()).extracting(SheetColumn::valueShape).containsExactly(
                SheetColumn.ValueShape.EMAIL,
                SheetColumn.ValueShape.NUMBER,
                SheetColumn.ValueShape.DATE);
    }

    @Test
    @DisplayName("refuses a file with more rows than one import may take, rather than truncating it")
    void refusesAnOversizedFile() {
        SpreadsheetReader capped = new SpreadsheetReader(propertiesWith(2));
        MockMultipartFile file = csv("""
                Company
                One
                Two
                Three
                """);

        assertThatThrownBy(() -> capped.read(file))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo(ErrorCode.IMPORT_TOO_MANY_ROWS);
    }

    @Test
    @DisplayName("tells the caller the ceiling it went over, which is the part they can act on")
    void namesTheRowCeiling() {
        // A configured limit is not request input, so ApiException's own rule allows saying it — and
        // "more rows than one import can take" without the number leaves the reader guessing.
        SpreadsheetReader capped = new SpreadsheetReader(propertiesWith(2));

        assertThatThrownBy(() -> capped.read(csv("Company\nOne\nTwo\nThree\n")))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getClientDetail())
                .asString()
                .contains("2");
    }

    @Test
    @DisplayName("refuses a file with nothing in it")
    void refusesAnEmptySheet() {
        assertThatThrownBy(() -> reader.read(csv("\n\n")))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo(ErrorCode.IMPORT_FILE_UNREADABLE);
    }

    @Test
    @DisplayName("refuses a content type the allowlist does not carry")
    void refusesAnUnlistedContentType() {
        MockMultipartFile pdf = new MockMultipartFile("file", "list.pdf", "application/pdf",
                "Company\nACWA Power\n".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> reader.read(pdf))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getCode())
                .isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    private static MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "list.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] workbook(List<String> headers, List<List<String>> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Longlist");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                header.createCell(i).setCellValue(headers.get(i));
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<String> values = rows.get(r);
                for (int c = 0; c < values.size(); c++) {
                    String value = values.get(c);
                    if (value.matches("\\d+")) {
                        row.createCell(c).setCellValue(Double.parseDouble(value));
                    } else {
                        row.createCell(c).setCellValue(value);
                    }
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] joined = new byte[first.length + second.length];
        System.arraycopy(first, 0, joined, 0, first.length);
        System.arraycopy(second, 0, joined, first.length, second.length);
        return joined;
    }

    private static LightMoveProperties propertiesWith(int maxRows) {
        return new LightMoveProperties(null, null, null, null, null, null, null,
                new SpreadsheetImportSettings(10_485_760L, maxRows, false,
                        List.of("text/csv", "text/plain",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")));
    }
}
