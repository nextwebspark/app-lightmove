package app.lightmove.api.core.llm.model;

import app.lightmove.api.core.llm.constant.AiProvider;
import app.lightmove.api.core.llm.constant.FinishKind;

/**
 * One counted call as it passes through the advisors: which version it was asked with, and how far it got. Carried in
 * the advisor context because the guard answers without reaching the counter, and a bind failure happens after it.
 * One thread, one call.
 */
public final class PromptCallTrace {

    private final PromptVersionRef version;
    private final AiProvider provider;
    private final boolean reasksOnSchemaFailure;
    private String model;
    private int answers;
    private FinishKind lastFinish;
    private boolean modelCallFailed;

    public PromptCallTrace(PromptVersionRef version, AiProvider provider, String askedModel,
                           boolean reasksOnSchemaFailure) {
        this.version = version;
        this.provider = provider;
        this.model = askedModel;
        this.reasksOnSchemaFailure = reasksOnSchemaFailure;
    }

    public PromptVersionRef version() {
        return version;
    }

    public AiProvider provider() {
        return provider;
    }

    /** The model that last answered, else the one asked; null when neither was named. */
    public String model() {
        return model;
    }

    /** Records one answer of the model. True when it is a second answer to a prompt whose only repeat is a re-ask. */
    public boolean answeredBy(String answeringModel, FinishKind finish) {
        if (answeringModel != null && !answeringModel.isBlank()) {
            model = answeringModel;
        }
        lastFinish = finish;
        answers++;
        return reasksOnSchemaFailure && answers > 1;
    }

    public void modelCallFailed() {
        modelCallFailed = true;
    }

    /** The call came back without the model ever answering: the guard answered in its place. */
    public boolean wasNeverAnswered() {
        return answers == 0 && !modelCallFailed;
    }

    /** The model's last call succeeded, so a failure after it is the answer's, not the provider's. */
    public boolean wasAnswered() {
        return answers > 0 && !modelCallFailed;
    }

    /**
     * The last answer was cut off or stopped by the safety filter. It is already a failure under its finish reason,
     * so that it then would not bind is the same failure and not a second one.
     */
    public boolean wasCutShort() {
        return lastFinish == FinishKind.MAX_TOKENS || lastFinish == FinishKind.SAFETY;
    }
}
