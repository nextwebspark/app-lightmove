package app.lightmove.api.core.text.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CsvFormatterTest {

    @Test
    @DisplayName("quotes only a value that would otherwise break the row, doubling its quotes")
    void quotesOnlyWhereNeeded() {
        assertThat(CsvFormatter.row(List.of("plain", "a,b", "say \"hi\"", "two\nlines")))
                .isEqualTo("plain,\"a,b\",\"say \"\"hi\"\"\",\"two\nlines\"");
        assertThat(CsvFormatter.row(Arrays.asList("x", null))).isEqualTo("x,");
    }

    @Test
    @DisplayName("defuses a leading formula starter, whitespace included, and leaves other text alone")
    void defusesFormulaStarters() {
        assertThat(CsvFormatter.defused("=HYPERLINK(\"x\")")).isEqualTo("'=HYPERLINK(\"x\")");
        assertThat(CsvFormatter.defused("+966 50 000 0000")).isEqualTo("'+966 50 000 0000");
        assertThat(CsvFormatter.defused("\t=1+1")).isEqualTo("'\t=1+1");
        assertThat(CsvFormatter.defused("Acme = good")).isEqualTo("Acme = good");
        assertThat(CsvFormatter.defused("")).isEmpty();
    }
}
