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
                List.of("2. Second point", "# Not a heading", "@[Yara](user:00000000-0000-0000-0000-000000000000)")),
                null, 0);

        assertThat(body).doesNotContain("https://", "www.", "it@evil", "[click]", "[Yara]")
                .contains("https:" + WORD_JOINER + "//evil.example", "www" + WORD_JOINER + ".evil.example",
                        "it@" + WORD_JOINER + "evil.example", "\\[click\\]")
                .contains("- 2\\. Second point", "- \\# Not a heading", "\\[Yara\\](user:");
    }

    @Test
    @DisplayName("a call that stopped early, or could not all be heard, says so first")
    void partialCallsSaySo() {
        assertThat(MeetingNoteMarkdown.of(summary("Spoke about the role.", List.of()), "14:32", 0))
                .startsWith("**Partial — recording stopped at 14:32**");
        assertThat(MeetingNoteMarkdown.of(summary("Spoke about the role.", List.of()), null, 2))
                .startsWith("**Partial — about 2 minutes of the call could not be transcribed**");
        assertThat(MeetingNoteMarkdown.of(summary("Spoke about the role.", List.of()), null, 0))
                .startsWith("Spoke about the role.");
    }

    private static MeetingSummary summary(String overview, List<String> keyPoints) {
        return new MeetingSummary(overview, keyPoints, null, null, null, null, List.of(), List.of(), List.of(),
                List.of(), List.of());
    }
}
