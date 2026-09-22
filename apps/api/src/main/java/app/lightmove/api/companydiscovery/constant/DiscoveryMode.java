package app.lightmove.api.companydiscovery.constant;

/**
 * How an answer was obtained. Reported to the caller and recorded in the audit trail, because a
 * grounded answer and a fallback are different products and neither should arrive silently.
 */
public enum DiscoveryMode {

    /** One grounded call that also honoured a response schema. The cheap path. */
    GROUNDED_STRUCTURED,

    /**
     * Grounded prose, then a second ungrounded call to read it into the schema. Two billed calls,
     * used where Vertex refuses grounding and a schema together.
     */
    GROUNDED_PROSE_EXTRACTED,

    /** Nothing came back — the provider is off, unreachable, or refused. Never a guess instead. */
    UNAVAILABLE
}
