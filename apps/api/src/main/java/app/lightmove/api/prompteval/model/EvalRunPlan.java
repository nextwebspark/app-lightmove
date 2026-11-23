package app.lightmove.api.prompteval.model;

import app.lightmove.api.core.llm.model.ServedPrompt;
import app.lightmove.api.core.llm.service.EvalSubject;
import app.lightmove.api.prompteval.constant.EvalCaseSplit;
import app.lightmove.api.prompteval.constant.EvalRepeatsReason;

/** A run as it would be started: both versions resolved, and how many calls answering every case would take. */
public record EvalRunPlan(EvalDataset dataset, EvalSubject subject, EvalCaseSplit split, ServedPrompt candidate,
                          ServedPrompt baseline, int cases, int repeats, EvalRepeatsReason repeatsReason) {

    public int calls() {
        return cases * 2 * repeats;
    }
}
