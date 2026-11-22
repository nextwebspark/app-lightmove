package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.llm.model.ServedPrompt;

/** Where a call gets its system prompt: the version serving now, with the model and settings saved beside it. */
public interface PromptSource {

    ServedPrompt current(String promptId);
}
