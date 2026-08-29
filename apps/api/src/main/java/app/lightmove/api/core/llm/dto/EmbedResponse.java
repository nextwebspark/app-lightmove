package app.lightmove.api.core.llm.dto;

/** The embedding vector for one piece of text, and its length for convenience. */
public record EmbedResponse(int dimensions, float[] embedding) {}
