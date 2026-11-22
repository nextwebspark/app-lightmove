package app.lightmove.api.notetaker.service;

import app.lightmove.api.common.constant.CompetencyPanel;
import app.lightmove.api.core.llm.service.PromptTrial;
import app.lightmove.api.notetaker.constant.InterviewQuestionSection;
import app.lightmove.api.notetaker.constant.InterviewQuestionSource;
import app.lightmove.api.notetaker.constant.RecordGap;
import app.lightmove.api.notetaker.model.InterviewQuestion;
import app.lightmove.api.notetaker.model.LiveCoaching;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The live coach's Try it: an invented call so far, read against an invented brief and plan. Nobody here exists. */
@Component
@RequiredArgsConstructor
class InterviewCoachTrial implements PromptTrial {

    private static final List<InterviewQuestion> PLAN = List.of(
            new InterviewQuestion("trial-fit-network", InterviewQuestionSection.FIT, CompetencyPanel.TECHNICAL,
                    InterviewPlanTrial.NETWORK_OPERATIONS, null, "Tell me about the network you run.",
                    "Sites, fleet size and a result he owned", null, true, InterviewQuestionSource.AI),
            new InterviewQuestion("trial-fit-warehousing", InterviewQuestionSection.FIT, CompetencyPanel.TECHNICAL,
                    "Warehousing", null, "How much of the storage side has been yours to run?",
                    "Direct ownership of temperature-controlled sites", null, true, InterviewQuestionSource.AI),
            new InterviewQuestion("trial-gap-notice", InterviewQuestionSection.GAP, null, null, RecordGap.NOTICE,
                    "What notice are you on now?", null, null, true, InterviewQuestionSource.AI),
            new InterviewQuestion("trial-clarify-move", InterviewQuestionSection.CLARIFY, null, null, null,
                    "What led you to leave Quillhaven Haulage?", null, "The reason is not on file", true,
                    InterviewQuestionSource.MANUAL));

    private static final Set<RecordGap> MISSING = EnumSet.of(RecordGap.NOTICE, RecordGap.PACKAGE,
            RecordGap.RELOCATION);

    private static final List<InterviewCoach.HeardLine> HEARD = MeetingSummaryTrial.TRANSCRIPT.stream()
            .map(line -> new InterviewCoach.HeardLine(line.at(), line.speaker(), line.text(),
                    line.speaker().equals(MeetingSummaryTrial.CONSULTANT),
                    line.speaker().equals(MeetingSummaryTrial.EXECUTIVE)))
            .toList();

    private final InterviewCoach coach;

    @Override
    public String promptId() {
        return "interview-coach";
    }

    @Override
    public String inputName() {
        return "Four minutes in";
    }

    @Override
    public String inputDescription() {
        return "A fictional call's first four minutes, with four prepared questions, three facts the record is "
                + "missing and nothing found yet. No workspace's data is used.";
    }

    @Override
    public String userMessageTemplate() {
        return InterviewCoach.USER_MESSAGE;
    }

    @Override
    public void run() {
        coach.coach(InterviewPlanTrial.BRIEF, PLAN, MISSING, LiveCoaching.empty(), HEARD,
                MeetingSummaryTrial.CALL_START);
    }
}
