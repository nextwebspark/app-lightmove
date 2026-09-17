package app.lightmove.api.core.audit.constant;

/**
 * Platform-tier audit events: changes to what every workspace shares, recorded with no workspace.
 * The two role events are written by {@code ops/cloudsql/grant-platform-role.sql}, not by the app.
 */
public enum PlatformEventType implements AuditEventType {

    POSITION_TEMPLATE_LIBRARY_CREATED,
    POSITION_TEMPLATE_LIBRARY_UPDATED,
    POSITION_TEMPLATE_LIBRARY_ARCHIVED,
    POSITION_TEMPLATE_LIBRARY_RESTORED,
    POSITION_TEMPLATE_LIBRARY_IMPORTED,

    PLATFORM_ROLE_GRANTED,
    PLATFORM_ROLE_REVOKED;

    @Override
    public String code() {
        return name();
    }
}
