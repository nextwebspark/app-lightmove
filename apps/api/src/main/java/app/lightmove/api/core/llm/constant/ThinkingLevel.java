package app.lightmove.api.core.llm.constant;

/** How much a Gemini 3 or later model reasons before it answers, least first; each model offers its own subset. */
public enum ThinkingLevel {
    MINIMAL,
    LOW,
    MEDIUM,
    HIGH
}
