package app.lightmove.api.workspace.constant;

/** Who an audit log line names: a person of the workspace, Uncava's own staff, an API key with no owner, or nobody. */
public enum AuditActorKind {
    MEMBER,
    /** Uncava staff acting from the platform: named as Uncava, never by name or address. */
    UNCAVA,
    /** A workspace API key, which belongs to nobody. */
    API_KEY,
    /** A provider's webhook or a scheduled job. */
    SYSTEM
}
