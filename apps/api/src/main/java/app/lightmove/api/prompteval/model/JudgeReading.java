package app.lightmove.api.prompteval.model;

import app.lightmove.api.core.llm.model.PromptTrialResult;
import java.util.Map;

/** What the judge said of one pair, by criterion key, and what asking cost. */
public record JudgeReading(Map<String, JudgedPick> picks, PromptTrialResult asked) {

    /**
     * False when nothing came back at all, which is an outage and not a pick. A call the injection guard refused
     * before it was made did come back, with no picks: asking again would be refused again.
     */
    public boolean isAnswered() {
        return asked.modelCalls() > 0 || asked.parsedAnswer() != null;
    }

    /**
     * The calls one ask counts as, on a run and on a check alike: the model's own, and never fewer than one, so an
     * ask that came back with nothing still moves its job toward its call cap.
     */
    public int callsCharged() {
        return Math.max(1, asked.modelCalls());
    }
}
