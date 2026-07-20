package app.lightmove.api.core.audit.constant;

/**
 * Every security-relevant thing that can happen, as a closed set spread across feature-scoped enums.
 *
 * <p>A sealed interface rather than a free-form string so that a typo cannot silently create a new
 * event type that no alert or report is watching for — the guarantee holds at every write site,
 * because only one of the permitted enum constants can be passed to {@code audit.event(...)}. The
 * split mirrors the RBAC catalogs ({@code WorkspaceRole}/{@code ProjectRole}/…): each domain names
 * its own events, and this stays the one type the ledger writes.
 *
 * <p>The permitted enums live here in {@code core/audit/constant}, not in the features, because
 * {@code core} cannot depend on a feature — the same reason the RBAC role enums live in {@code core}.
 */
public sealed interface AuditEventType
        permits AuthEventType, WorkspaceEventType, ProjectEventType, SecurityEventType {

    /** The stored value — {@code Enum.name()}, persisted verbatim to {@code app_lm_audit_event}. */
    String code();
}
