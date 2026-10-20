package app.lightmove.api.common.note;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.common.constant.NoteFormat;
import org.junit.jupiter.api.Test;

class NoteTextTest {

    @Test
    void readsAFormattedNoteAsPlainWords() {
        String body = """
                ## Call summary

                **Wants** ++Dubai++ and ~~Riyadh~~, says @[Sara Al-Mansour](user:6f1c2a9e-0000-4000-8000-000000000001)

                - Send the brief
                - [x] Booked the call
                - [ ] Ask for the CV

                1. First
                2. Second

                > Quoted from the client

                See [their site](https://example.com) \\*not italic\\* 1\\. not a list, or [https://uncava.com](https://uncava.com)""";

        String plain = NoteText.plainTextOf(body, NoteFormat.MARKDOWN);

        assertThat(plain).contains("Call summary", "Wants Dubai and Riyadh, says @Sara Al-Mansour")
                .contains("- Send the brief", "- [x] Booked the call", "- [ ] Ask for the CV", "1. First", "2. Second")
                .contains("Quoted from the client", "See their site *not italic* 1. not a list, or https://uncava.com")
                .doesNotContain("**", "++", "~~", "user:", "##", "/Riyadh/", "«", "\"", "(https://");
    }

    @Test
    void leavesAPlainNoteAsTypedButForItsMentions() {
        String body = "**not bold** for @[team](team)\n1. not a list";

        assertThat(NoteText.plainTextOf(body, NoteFormat.PLAIN)).isEqualTo("**not bold** for @team\n1. not a list");
    }

    @Test
    void keepsTheMarkdownForThePromptButNoId() {
        String body = "**Wants** Dubai, says @[Omar Khalil](user:6f1c2a9e-0000-4000-8000-000000000002)";

        assertThat(NoteText.forPrompt(body)).isEqualTo("**Wants** Dubai, says @Omar Khalil");
    }
}
