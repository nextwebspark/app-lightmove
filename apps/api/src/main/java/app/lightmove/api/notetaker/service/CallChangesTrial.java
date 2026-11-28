package app.lightmove.api.notetaker.service;

import app.lightmove.api.candidate.model.CallHeldFields;
import app.lightmove.api.core.llm.service.PromptTrial;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The call changes' Try it: an invented call read against an invented record it contradicts. Nobody here exists. */
@Component
@RequiredArgsConstructor
class CallChangesTrial implements PromptTrial {

    private static final UUID RELOCATION_TAG = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private static final CallHeldFields HELD = new CallHeldFields("3 months", "AED", 900_000L, null, null, null,
            "Dubai", "United Arab Emirates", null, RELOCATION_TAG, false, false);

    private final CallChangeSuggester suggester;

    @Override
    public String promptId() {
        return "call-changes";
    }

    @Override
    public String inputName() {
        return "Record out of date";
    }

    @Override
    public String inputDescription() {
        return "A fictional first call in which an operations chief states a notice period, a package and a city "
                + "that differ from an invented record. No workspace's data is used.";
    }

    @Override
    public String userMessageTemplate() {
        return CallChangeSuggester.USER_MESSAGE;
    }

    @Override
    public void run() {
        suggester.suggest(MeetingSummaryTrial.TRANSCRIPT, MeetingSummaryTrial.CALL_START, HELD);
    }
}
