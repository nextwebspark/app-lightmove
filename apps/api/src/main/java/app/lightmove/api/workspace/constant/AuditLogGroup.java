package app.lightmove.api.workspace.constant;

import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.audit.constant.AuditEventType;
import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.constant.PlatformEventType;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.constant.SecurityEventType;
import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.experimental.Accessors;

/** The audit log's Category filter: which event types each one shows. */
@Getter
@Accessors(fluent = true)
public enum AuditLogGroup implements ApiValueEnum {

    SIGN_IN("signIn", "Sign-in",
            AuthEventType.LOGIN_SUCCEEDED, AuthEventType.OAUTH_LOGIN_SUCCEEDED, AuthEventType.LOGIN_FAILED,
            AuthEventType.ACCOUNT_LOCKED, AuthEventType.MFA_ENABLED, AuthEventType.MFA_DISABLED,
            AuthEventType.MFA_CHALLENGE_FAILED, AuthEventType.MFA_RECOVERY_CODE_USED,
            AuthEventType.MFA_RECOVERY_CODES_REGENERATED, AuthEventType.PASSWORD_CHANGED,
            AuthEventType.PASSWORD_RESET_COMPLETED, AuthEventType.TOKEN_REUSE_DETECTED,
            AuthEventType.SESSION_REVOKED, AuthEventType.OTHER_SESSIONS_REVOKED, AuthEventType.WORKSPACE_SWITCHED,
            AuthEventType.EXTENSION_PAIRED),
    MEMBERS_AND_ACCESS("membersAndAccess", "Members & access",
            WorkspaceEventType.WORKSPACE_CREATED, WorkspaceEventType.MEMBER_INVITED,
            WorkspaceEventType.INVITATION_ACCEPTED, WorkspaceEventType.INVITATION_REVOKED,
            WorkspaceEventType.MEMBER_ROLE_CHANGED, WorkspaceEventType.MEMBER_REMOVED,
            ProjectEventType.PROJECT_TEAM_CHANGED, ProjectEventType.CLIENT_REP_INVITED,
            ProjectEventType.CLIENT_REP_ACCEPTED, ProjectEventType.CANDIDATE_CLIENT_VISIBILITY_CHANGED,
            WorkspaceEventType.SEAT_ADDED, WorkspaceEventType.SEAT_REMOVED),
    EXPORTS_AND_DOWNLOADS("exportsAndDownloads", "Exports & downloads",
            ProjectEventType.COMPANIES_EXPORTED, ProjectEventType.CANDIDATES_EXPORTED,
            ProjectEventType.PERSON_DOCUMENT_DOWNLOADED, ProjectEventType.OUTREACH_ATTACHMENT_DOWNLOADED,
            ProjectEventType.NOTETAKER_RECORDING_DOWNLOADED, WorkspaceEventType.AUDIT_LOG_EXPORTED),
    SETTINGS("settings", "Settings",
            WorkspaceEventType.WORKSPACE_UPDATED, WorkspaceEventType.WORKSPACE_DELETED,
            WorkspaceEventType.POSITION_TEMPLATE_CREATED, WorkspaceEventType.POSITION_TEMPLATE_CUSTOMISED,
            WorkspaceEventType.POSITION_TEMPLATE_UPDATED, WorkspaceEventType.POSITION_TEMPLATE_RESET,
            WorkspaceEventType.POSITION_TEMPLATE_DELETED, WorkspaceEventType.POSITION_TEMPLATE_HIDDEN,
            WorkspaceEventType.POSITION_TEMPLATE_SHOWN, WorkspaceEventType.POSITION_TEMPLATES_IMPORTED,
            ProjectEventType.CANDIDATE_TAG_CREATED, ProjectEventType.CANDIDATE_TAG_UPDATED,
            ProjectEventType.CANDIDATE_PIPELINE_REORDERED, ProjectEventType.TASK_PRESET_CREATED,
            ProjectEventType.TASK_PRESET_UPDATED, ProjectEventType.TASK_PRESETS_REORDERED,
            ProjectEventType.TASK_RULES_CHANGED, WorkspaceEventType.SUBSCRIPTION_CHANGED,
            WorkspaceEventType.CREDITS_PURCHASED, WorkspaceEventType.BILLING_CHECKOUT_STARTED,
            WorkspaceEventType.BILLING_PORTAL_OPENED, WorkspaceEventType.FAIR_USE_REACHED,
            PlatformEventType.CREDITS_GRANTED, PlatformEventType.INVOICED_SUBSCRIPTION_SET),
    API_KEYS_AND_AI_APPS("apiKeysAndAiApps", "API keys & AI apps",
            WorkspaceEventType.API_KEY_CREATED, WorkspaceEventType.API_KEY_REVOKED,
            WorkspaceEventType.OAUTH_GRANT_CREATED, WorkspaceEventType.OAUTH_GRANT_REVOKED,
            ProjectEventType.PUBLIC_API_READ, ProjectEventType.MCP_TOOL_CALL),
    OUTREACH("outreach", "Outreach",
            WorkspaceEventType.MAILBOX_CONNECTED, WorkspaceEventType.MAILBOX_DISCONNECTED,
            WorkspaceEventType.ZOOM_CONNECTED, WorkspaceEventType.ZOOM_DISCONNECTED,
            ProjectEventType.OUTREACH_SEQUENCE_CREATED, ProjectEventType.OUTREACH_SEQUENCE_UPDATED,
            ProjectEventType.OUTREACH_SEQUENCE_DELETED, ProjectEventType.OUTREACH_ENROLLED,
            ProjectEventType.OUTREACH_EMAIL_SENT, ProjectEventType.OUTREACH_REPLIED,
            ProjectEventType.OUTREACH_BOUNCED, ProjectEventType.OUTREACH_STOPPED,
            ProjectEventType.OUTREACH_MESSAGE_SCHEDULED, ProjectEventType.OUTREACH_MESSAGE_SENT,
            ProjectEventType.OUTREACH_MESSAGE_FAILED, ProjectEventType.OUTREACH_MESSAGE_CANCELLED,
            ProjectEventType.OUTREACH_MEETING_BOOKED, ProjectEventType.OUTREACH_MEETING_RESCHEDULED,
            ProjectEventType.OUTREACH_MEETING_CANCELLED, ProjectEventType.OUTREACH_OPENERS_DRAFTED,
            ProjectEventType.NOTETAKER_SCHEDULED, ProjectEventType.NOTETAKER_CANCELLED,
            ProjectEventType.NOTETAKER_JOINED_NOW, ProjectEventType.NOTETAKER_ENDED_EARLY,
            ProjectEventType.NOTETAKER_NOTE_FILED, ProjectEventType.NOTETAKER_SUGGESTIONS_ASKED,
            ProjectEventType.NOTETAKER_SUGGESTION_APPLIED, ProjectEventType.NOTETAKER_SUGGESTION_SKIPPED,
            ProjectEventType.INTERVIEW_PLAN_DRAFTED, ProjectEventType.INTERVIEW_PLAN_SAVED,
            ProjectEventType.NOTETAKER_COPILOT_ENABLED, ProjectEventType.NOTETAKER_COPILOT_DISABLED,
            ProjectEventType.NOTETAKER_COPILOT_STOPPED, ProjectEventType.NOTETAKER_COPILOT_OPENED);

    /**
     * An account's own security events, written with no workspace: each shows in every workspace the person is a
     * member of. A successful sign-in is stamped with the workspace it opened into instead, so it is not here.
     */
    public static final Set<String> ACCOUNT_SECURITY_CODES = codesOf(
            AuthEventType.LOGIN_FAILED, AuthEventType.ACCOUNT_LOCKED, AuthEventType.MFA_ENABLED,
            AuthEventType.MFA_DISABLED, AuthEventType.MFA_CHALLENGE_FAILED, AuthEventType.MFA_RECOVERY_CODE_USED,
            AuthEventType.MFA_RECOVERY_CODES_REGENERATED, AuthEventType.PASSWORD_CHANGED,
            AuthEventType.PASSWORD_RESET_COMPLETED, AuthEventType.TOKEN_REUSE_DETECTED,
            AuthEventType.SESSION_REVOKED, AuthEventType.OTHER_SESSIONS_REVOKED);

    /** Never listed: a background refresh or sign-out on every tab is noise, and the rest are Uncava's own. */
    public static final Set<String> HIDDEN_CODES = codesOf(
            AuthEventType.TOKEN_REFRESHED, AuthEventType.LOGOUT, WorkspaceEventType.OAUTH_TOKEN_REFRESHED,
            WorkspaceEventType.OAUTH_CLIENT_REGISTERED, WorkspaceEventType.CREDIT_BALANCE_DRIFT,
            SecurityEventType.RATE_LIMIT_EXCEEDED);

    private final String value;
    /** The Category column's word in the CSV; the screen spells its own. */
    private final String label;
    private final Set<String> codes;

    AuditLogGroup(String value, String label, AuditEventType... types) {
        this.value = value;
        this.label = label;
        this.codes = codesOf(types);
    }

    /** The group a type is listed under, if any; an ungrouped type still shows under All. */
    public static Optional<AuditLogGroup> of(String code) {
        return Arrays.stream(values()).filter(group -> group.codes.contains(code)).findFirst();
    }

    private static Set<String> codesOf(AuditEventType... types) {
        return Stream.of(types).map(AuditEventType::code).collect(Collectors.toUnmodifiableSet());
    }
}
