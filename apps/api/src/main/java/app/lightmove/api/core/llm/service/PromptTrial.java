package app.lightmove.api.core.llm.service;

/**
 * One prompt's Try it: a fictional input, run through the calling feature's own code, so the user message, the
 * guard, the schema check and the repair are the ones production uses. Implemented beside each caller.
 *
 * <p><b>An implementation reads no table.</b> Its input is written in code or bundled with it, and it calls only
 * the part of its feature that takes the material as arguments: the platform sits inside no workspace.
 */
public interface PromptTrial {

    String promptId();

    /** A few words naming the fictional input, as the console titles it. */
    String inputName();

    /** One or two sentences on what the input holds. */
    String inputDescription();

    /** The message the feature sends with its instructions, placeholders unfilled. Never data. */
    String userMessageTemplate();

    /** Asks the model once with the fictional input. What it answers is recorded by {@link PromptTrialRunner}. */
    void run();
}
