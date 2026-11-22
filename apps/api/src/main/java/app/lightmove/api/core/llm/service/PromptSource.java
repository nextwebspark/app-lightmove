package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.llm.constant.PromptKind;
import app.lightmove.api.core.llm.model.ServedPrompt;

/**
 * Where a call gets its system prompt: the version serving now, with the model and settings saved beside it. The
 * kind is asked because a version's model answers only a kind of prompt it is able to.
 */
public interface PromptSource {

    ServedPrompt current(String promptId, PromptKind kind);
}
