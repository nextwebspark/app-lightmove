package app.lightmove.api.core.llm.dto;

/** The model's shortlist/decline recommendation and reasoning, as returned — no parsing applied. */
public record ShortlistResponse(String verdict) {}
