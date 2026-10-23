package app.lightmove.api.notetaker.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.notetaker.model.MeetingSummary;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MeetingNoteMarkdownTest {

    private static final String WORD_JOINER = "⁠";

    @Test
    @DisplayName("nothing anyone said on the call becomes a link, a mention, a heading or a list")
    void theModelsWordsStayWords() {
        String body = MeetingNoteMarkdown.of(summary(
                "Go to https://evil.example or www.evil.example, write to it@evil.example, or [click](https://x.y)",
                List.of("2. Second point", "# Not a heading", "@[Yara](user:00000000-0000-0000-0000-000000000000)")));

        assertThat(body).doesNotContain("https://", "www.", "it@evil", "[click]", "[Yara]")
                .contains("https:" + WORD_JOINER + "//evil.example", "www" + WORD_JOINER + ".evil.example",
                        "it@" + WORD_JOINER + "evil.example", "\\[click\\]")
                .contains("- 2\\. Second point", "- \\# Not a heading", "\\[Yara\\](user:");
    }

    @Test
    @DisplayName("the overview leads, and a section the call never touched is left out")
    void emptySectionsAreLeftOut() {
        String body = MeetingNoteMarkdown.of(new MeetingSummary("Spoke about the role.", List.of("Open to move"),
                null, "AED 90k a month", null, null, List.of(), List.of("Send the brief"), List.of(), List.of(),
                List.of()));

        assertThat(body).startsWith("Spoke about the role.")
                .contains("## Key points\n\n- Open to move")
                .contains("- **Package:** AED 90k a month", "## Next steps")
                .doesNotContain("## Concerns", "## Against the brief", "Motivation");
    }

    private static MeetingSummary summary(String overview, List<String> keyPoints) {
        return new MeetingSummary(overview, keyPoints, null, null, null, null, List.of(), List.of(), List.of(),
                List.of(), List.of());
    }
}
