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
    PLATFORM_STEP_UP_PASSED,
    PLATFORM_STEP_UP_FAILED,

    PROMPT_VERSION_SAVED,
    PROMPT_TRIED,
    PROMPT_PUT_LIVE,
    PROMPT_ROLLED_BACK,

    EVAL_CASES_IMPORTED,
    EVAL_CASE_RETIRED,
    EVAL_RUN_STARTED,
    EVAL_RUN_FINISHED,
    EVAL_RUN_CANCELLED,
    EVAL_GATE_OVERRIDDEN;

    @Override
    public String code() {
        return name();
    }
}
