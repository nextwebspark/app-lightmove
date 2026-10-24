package app.lightmove.api.notetaker.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.notetaker.model.CompetencyEvidence;
import app.lightmove.api.notetaker.model.MeetingSummary;
import app.lightmove.api.notetaker.model.StatedFact;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CallRecordMarkdownTest {

    @Test
    @DisplayName("the record carries the account, every fact with its time and who said it, and the evidence's quotes")
    void recordKeepsWhatTheNoteLeavesOut() {
        String record = CallRecordMarkdown.of(new MeetingSummary("Spoke about the CFO role.", List.of("Led an IPO"),
                "Wants a listed company", null, "Three months", null, List.of("Counter-offer likely"),
                List.of("Send the brief"),
                List.of(new CompetencyEvidence("Capital markets", "Took the company through its IPO.", "14:05",
                                "I ran the roadshow myself", true),
                        new CompetencyEvidence("Team leadership", "Says they lead well.", null, null, false)),
                List.of("Digital transformation"), List.of("Based in Riyadh, the role is in Dubai"),
                "They opened on the IPO, moved to the team, and closed on timing.",
                List.of(new StatedFact("Team size", "Forty in finance", "08:10", false),
                        new StatedFact("Package", "Around AED 1.2M base", "30:00", true)),
                List.of("Their expected package")));

        assertThat(record).startsWith("Spoke about the CFO role.")
                .contains("## Account of the call\n\nThey opened on the IPO")
                .contains("- **Motivation:** Wants a listed company", "- **Notice:** Three months",
                        "- **Team size:** Forty in finance [08:10]",
                        "- **Package:** Around AED 1.2M base [30:00] (said by the team, not the executive)")
                .contains("- **Capital markets** (with an example): Took the company through its IPO. [14:05] "
                                + "Said: \"I ran the roadshow myself\"",
                        "- **Team leadership** (a claim, no example): Says they lead well.",
                        "- **Not covered:** Digital transformation")
                .contains("## Where it cuts against the brief\n\n- Based in Riyadh",
                        "## Open questions\n\n- Their expected package", "## Next steps\n\n- Send the brief");
    }

    @Test
    @DisplayName("a quote is kept only where the transcript holds those words in that order")
    void quotesAreCheckedAgainstTheTranscript() {
        String heard = MeetingSummariser.wordsOf(
                "So, I ran the road-show myself — all of it. أنا قدت الفريق.");

        assertThat(MeetingSummariser.quoteOrNull("I ran the road-show myself", heard))
                .isEqualTo("I ran the road-show myself");
        assertThat(MeetingSummariser.quoteOrNull("i RAN the road show, myself", heard)).isNotNull();
        assertThat(MeetingSummariser.quoteOrNull("أنا قدت الفريق", heard)).isNotNull();
        assertThat(MeetingSummariser.quoteOrNull("I ran the whole roadshow myself", heard)).isNull();
        assertThat(MeetingSummariser.quoteOrNull("ran the road", heard)).isNotNull();
        assertThat(MeetingSummariser.quoteOrNull("ran the roa", heard)).isNull();
        assertThat(MeetingSummariser.quoteOrNull(" — ", heard)).isNull();
        assertThat(MeetingSummariser.quoteOrNull(null, heard)).isNull();
    }
}
