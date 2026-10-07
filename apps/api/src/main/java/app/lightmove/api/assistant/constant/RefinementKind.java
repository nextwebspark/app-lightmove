package app.lightmove.api.assistant.constant;

/** How a refinement changes the answer's search: widening it toward the target universe, or narrowing it. */
public enum RefinementKind {
    ADD_INDUSTRY,
    ADD_COUNTRY,
    ANY_SIZE,
    DROP_KEYWORD,
    SIZE_FLOOR,
    ONE_COUNTRY,
    DROP_INDUSTRY
}
