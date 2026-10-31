package app.lightmove.api.workspace.constant;

/** Who an audit log line names. Uncava's staff are named as Uncava, never by name or address. */
public enum AuditActorKind {
    MEMBER,
    UNCAVA,
    API_KEY,
    SYSTEM
}
