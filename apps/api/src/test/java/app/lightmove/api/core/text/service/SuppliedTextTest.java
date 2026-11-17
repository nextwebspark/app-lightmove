package app.lightmove.api.core.text.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class SuppliedTextTest {

    @Test
    @DisplayName("blank input becomes null, anything else is trimmed")
    void blankToNull() {
        assertThat(SuppliedText.blankToNull(null)).isNull();
        assertThat(SuppliedText.blankToNull(" \t ")).isNull();
        assertThat(SuppliedText.blankToNull("  Acme  ")).isEqualTo("Acme");
    }

    @Test
    @DisplayName("inner whitespace runs collapse to one space")
    void collapseWhitespaceToNull() {
        assertThat(SuppliedText.collapseWhitespaceToNull("  United \n Arab\tEmirates ")).isEqualTo("United Arab Emirates");
        assertThat(SuppliedText.collapseWhitespaceToNull("\n\t")).isNull();
        assertThat(SuppliedText.collapseWhitespaceToNull(null)).isNull();
    }

    @Test
    @DisplayName("a bare host gains https, and a non-http scheme is dropped rather than stored")
    void browsableUrlOrNull() {
        assertThat(SuppliedText.browsableUrlOrNull("acme.com")).isEqualTo("https://acme.com");
        assertThat(SuppliedText.browsableUrlOrNull("http://acme.com/x")).isEqualTo("http://acme.com/x");
        assertThat(SuppliedText.browsableUrlOrNull("javascript:alert(1)")).isNull();
        assertThat(SuppliedText.browsableUrlOrNull("  ")).isNull();
    }

    @Test
    @DisplayName("every shared sample is kept or dropped as the SPA's toBrowsableUrl says, so the two cannot drift")
    void browsableUrlSamplesTheSpaShares() throws Exception {
        JsonNode samples;
        try (InputStream file = getClass().getResourceAsStream("/browsable-urls.json")) {
            samples = new ObjectMapper().readTree(file);
        }
        assertThat(samples.size()).isGreaterThan(10);
        samples.forEach(sample -> assertThat(SuppliedText.browsableUrlOrNull(sample.get("input").asText()))
                .as(sample.get("input").asText())
                .isEqualTo(sample.get("kept").isNull() ? null : sample.get("kept").asText()));
    }
}
