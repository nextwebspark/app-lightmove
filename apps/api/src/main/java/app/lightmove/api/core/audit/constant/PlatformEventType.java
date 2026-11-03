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

    CREDITS_GRANTED,
    INVOICED_SUBSCRIPTION_SET,

    PLATFORM_ROLE_GRANTED,
    PLATFORM_ROLE_REVOKED,

    PLATFORM_SETTING_CHANGED,
    PLATFORM_SETTING_RESET,
    /** A prompt's id and version only: the body is never recorded. */
    PLATFORM_PROMPT_SAVED,
    PLATFORM_PROMPT_RESTORED,
    PLATFORM_PROMPT_REVERTED,
    PLATFORM_CONFIG_REFRESHED;

    @Override
    public String code() {
        return name();
    }
}
