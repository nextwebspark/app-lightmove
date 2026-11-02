package app.lightmove.api.core.platformconfig.service;

import app.lightmove.api.core.platformconfig.constant.PlatformPrompt;

/**
 * The one read of a system prompt's text: the active override where a super admin saved one, else the shipped file.
 * Asked on every call, so a saved prompt is the next call's.
 */
@FunctionalInterface
public interface PromptTexts {

    String systemText(String promptId);

    default String systemText(PlatformPrompt prompt) {
        return systemText(prompt.id());
    }

    /** The shipped files alone, for a test or an eval that builds its prompts without a database. */
    static PromptTexts shipped() {
        return new ShippedPrompts()::systemTextOf;
    }
}
