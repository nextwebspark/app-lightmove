package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.model.AssistantAnswer;
import app.lightmove.api.assistant.model.AssistantTurnPrompt;

/**
 * Runs one assistant turn against a model.
 *
 * <p><b>The seam is the turn, not the call.</b> An implementation owns its own loop, its own caching
 * strategy and its own provider controls, so swapping the model behind the assistant is one class
 * and not a rewrite. A port drawn one level lower — "send these messages, get text back" — would be
 * the intersection of every provider's features and would give up the thinking, effort and
 * prefix-caching controls that are most of the reason to prefer one.
 */
public interface AssistantTurnRunner {

    AssistantAnswer run(AssistantTurnPrompt prompt);
}
