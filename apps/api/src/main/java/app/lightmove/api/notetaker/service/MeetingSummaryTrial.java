package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.llm.service.PromptTrial;
import app.lightmove.api.notetaker.model.TranscriptLine;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The call note's Try it: an invented first call between a consultant and an operations chief. Nobody here exists. */
@Component
@RequiredArgsConstructor
class MeetingSummaryTrial implements PromptTrial {

    static final Instant CALL_START = Instant.parse("2026-10-20T09:00:00Z");

    static final String CONSULTANT = "Imogen Farrow";

    static final String EXECUTIVE = "Tomas Ellery";

    /** The invented call every Notetaker trial that reads a transcript reads. */
    static final List<TranscriptLine> TRANSCRIPT = List.of(
            said(20, CONSULTANT, "Thanks for making time, Tomas. Before the role itself, what notice are you on now?"),
            said(41, EXECUTIVE, "It went up when I joined the executive committee. It is six months now, not three."),
            said(65, CONSULTANT, "And the package today?"),
            said(78, EXECUTIVE,
                    "Base is 85 thousand dirhams a month, and the bonus last year was three months of base."),
            said(122, CONSULTANT, "Are you still in Dubai?"),
            said(130, EXECUTIVE, "No, we moved to Abu Dhabi in the summer. I would relocate to Jeddah for the right "
                    + "role, and my family is open to it."),
            said(175, CONSULTANT, "Tell me about the network you run."),
            said(190, EXECUTIVE, "I moved four depots onto one planning system and took on-time delivery from 81 "
                    + "to 94 per cent in two years. The drivers fought it for six months. Warehousing has never "
                    + "reported to me, and running the whole operation is what would make me move."),
            said(255, CONSULTANT,
                    "Understood. I will send you the role description on Thursday and come back with dates next week."));

    private final MeetingSummariser summariser;

    @Override
    public String promptId() {
        return "meeting-summary";
    }

    @Override
    public String inputName() {
        return "First call, with brief";
    }

    @Override
    public String inputDescription() {
        return "A fictional four-minute first call between a consultant and an operations chief, summarised against "
                + "an invented brief. No workspace's data is used.";
    }

    @Override
    public String userMessageTemplate() {
        return MeetingSummariser.USER_MESSAGE;
    }

    @Override
    public void run() {
        summariser.summarise(TRANSCRIPT, CALL_START, InterviewPlanTrial.BRIEF);
    }

    private static TranscriptLine said(long secondsIn, String speaker, String text) {
        return new TranscriptLine(CALL_START.plusSeconds(secondsIn), speaker, text, true);
    }
}
