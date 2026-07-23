package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.llm.model.BriefExtraction;

/** The one call the brief-document upload needs from whichever LLM provider is configured. */
public interface BriefLlmClient {

    /**
     * @param documentText the position description's extracted plain text.
     * @param positionTitle the mandate's role title, for context (e.g. disambiguating "the package"
     *                      section of a document that covers more than one role).
     * @throws app.lightmove.api.core.error.model.ApiException {@code BRIEF_EXTRACTION_FAILED} if the
     *         provider call fails or returns something that cannot be read as a {@link BriefExtraction}.
     */
    BriefExtraction extractBrief(String documentText, String positionTitle);
}
