package app.lightmove.api.core.audit.constant;

/**
 * Every security-relevant event type, a closed set across feature-scoped enums, so a typo cannot
 * mint a type no alert watches. The enums live in {@code core} because it cannot depend on a feature.
 */
public sealed interface AuditEventType
        permits AuthEventType, WorkspaceEventType, ProjectEventType, SecurityEventType, PlatformEventType {

    /** The stored value — {@code Enum.name()}, persisted verbatim to {@code app_lm_audit_event}. */
    String code();
}
