package app.lightmove.api.core.text.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FileNameSanitizerTest {

    @Test
    @DisplayName("strips any path, control characters and quotes a caller could forge a header or path with")
    void stripsPathAndControlCharacters() {
        assertThat(FileNameSanitizer.sanitize("../../etc/passwd", "x")).isEqualTo("passwd");
        assertThat(FileNameSanitizer.sanitize("C:\\Users\\me\\brief.pdf", "x")).isEqualTo("brief.pdf");
        assertThat(FileNameSanitizer.sanitize("a\"b\r\nc.csv", "x")).isEqualTo("abc.csv");
    }

    @Test
    @DisplayName("falls back when nothing usable is left, and caps the length at 255")
    void fallsBackAndCaps() {
        assertThat(FileNameSanitizer.sanitize(null, "import")).isEqualTo("import");
        assertThat(FileNameSanitizer.sanitize("  ", "import")).isEqualTo("import");
        assertThat(FileNameSanitizer.sanitize("dir/", "import")).isEqualTo("import");
        assertThat(FileNameSanitizer.sanitize("a".repeat(300), "x")).hasSize(255);
    }
}
