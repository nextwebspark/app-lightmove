package app.lightmove.api.core.llm.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** An escape JSON does not have loses its backslash; every escape JSON does have is left exactly as written. */
class JsonEscapeRepairAdvisorTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    @DisplayName("the escape Gemini wrote in a snippet parses once mended")
    void mendsTheShippedEscape() {
        String answer = "{\"snippet\":\"Monitor the \\协 implementation\"}";

        String mended = JsonEscapeRepairAdvisor.withValidEscapes(answer);

        assertThat(JSON.readTree(mended).get("snippet").asString()).isEqualTo("Monitor the 协 implementation");
    }

    @Test
    @DisplayName("valid escapes, an escaped backslash before a letter included, are untouched")
    void leavesValidEscapesAlone() {
        String answer = "{\"a\":\"quote \\\" slash \\/ tab \\t unicode \\u00e9 path C:\\\\x\"}";

        assertThat(JsonEscapeRepairAdvisor.withValidEscapes(answer)).isSameAs(answer);
    }

    @Test
    @DisplayName("a \\u not followed by four hex digits is mended like any other bad escape")
    void mendsAnIncompleteUnicodeEscape() {
        String answer = "{\"path\":\"C:\\users and \\u协\"}";

        String mended = JsonEscapeRepairAdvisor.withValidEscapes(answer);

        assertThat(JSON.readTree(mended).get("path").asString()).isEqualTo("C:users and u协");
    }

    @Test
    @DisplayName("a complete \\u escape ending the text is kept")
    void keepsAUnicodeEscapeAtTheEnd() {
        String answer = "caf\\u00e9";

        assertThat(JsonEscapeRepairAdvisor.withValidEscapes(answer)).isSameAs(answer);
    }

    @Test
    @DisplayName("an answer with no backslash at all is returned as it came")
    void leavesPlainAnswersAlone() {
        String answer = "{\"roleTitle\":\"Chief Financial Officer\"}";

        assertThat(JsonEscapeRepairAdvisor.withValidEscapes(answer)).isSameAs(answer);
    }
}
