package app.lightmove.api.candidate.model;

import app.lightmove.api.core.llm.model.PromptVersionRef;

/**
 * Which prompt versions made a background run's proposals: one for gender, years and seniority, one for the
 * nationality reading. Either is null when that half was not asked, or was answered by no registry version.
 */
public record AiBackgroundSource(PromptVersionRef background, PromptVersionRef nationality) {
}
