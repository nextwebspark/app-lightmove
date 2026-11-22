package app.lightmove.api.core.llm.model;

import app.lightmove.api.core.llm.constant.PromptOrigin;

/** Where a prompt's production label points, and whether that version came with a release. */
public record ProductionVersion(int version, PromptOrigin origin) {
}
