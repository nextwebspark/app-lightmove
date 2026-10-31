package app.lightmove.api.workspace.service;

import app.lightmove.api.core.audit.constant.AuditEventType;
import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.constant.PlatformEventType;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.constant.SecurityEventType;
import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.workspace.model.AuditLine;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * The audit log's one sentence per row. Each phrase reads only {@link AuditLine#text}'s allowlisted keys; a type
 * nobody phrased reads as its own name, and a type the code does not know reads as "Other activity" — never its
 * payload. Words follow {@code docs/glossary.md}.
 */
@Component
public class AuditLogPhrasing {

    static final String UNKNOWN_ACTIVITY = "Other activity";

    private static final Set<String> KNOWN_CODES = Stream.of(
                    AuthEventType.values(), WorkspaceEventType.values(), ProjectEventType.values(),
                    SecurityEventType.values(), PlatformEventType.values())
            .flatMap(Arrays::stream)
            .map(AuditEventType::code)
            .collect(Collectors.toUnmodifiableSet());

    private final Map<String, Function<AuditLine, String>> phrases = new HashMap<>();

    public AuditLogPhrasing() {
        signIn();
        membersAndAccess();
        exportsAndDownloads();
        settings();
        apiKeysAndAiApps();
        outreach();
        elsewhere();
    }

    public String phrase(AuditLine line) {
        Function<AuditLine, String> phrase = phrases.get(line.code());
        if (phrase != null) {
            return phrase.apply(line);
        }
        return KNOWN_CODES.contains(line.code()) ? humanised(line.code()) : UNKNOWN_ACTIVITY;
    }

    private void signIn() {
        on(AuthEventType.LOGIN_SUCCEEDED, line -> switch (String.valueOf(line.text("secondFactor"))) {
            case "RECOVERY_CODE" -> "Signed in with a recovery code";
            case "null" -> "Signed in";
            default -> "Signed in with two-factor authentication";
        });
        on(AuthEventType.OAUTH_LOGIN_SUCCEEDED, line -> "Signed in with " + providerName(line.text("provider")));
        on(AuthEventType.LOGIN_FAILED, line -> "Sign-in failed" + switch (String.valueOf(line.text("reason"))) {
            case "bad_password" -> ": wrong password";
            case "no_local_password" -> ": no password set";
            case "account_locked" -> ": account locked";
            default -> line.text("reason") != null && line.text("reason").startsWith("status_")
                    ? ": account not active" : "";
        });
        on(AuthEventType.ACCOUNT_LOCKED, line -> "Account locked after repeated failed sign-ins");
        on(AuthEventType.MFA_ENABLED, line -> line.failed()
                ? "Turning on two-factor authentication was refused" : "Turned on two-factor authentication");
        on(AuthEventType.MFA_DISABLED, line -> line.failed()
                ? "Turning off two-factor authentication was refused" : "Turned off two-factor authentication");
        on(AuthEventType.MFA_CHALLENGE_FAILED, line -> "Two-factor code refused at sign-in");
        on(AuthEventType.MFA_RECOVERY_CODE_USED, line -> "Used a recovery code");
        on(AuthEventType.MFA_RECOVERY_CODES_REGENERATED, line -> line.failed()
                ? "Making new recovery codes was refused" : "Made new recovery codes");
        on(AuthEventType.PASSWORD_CHANGED, line -> line.failed()
                ? "Password change refused: wrong current password" : "Changed their password");
        on(AuthEventType.PASSWORD_RESET_COMPLETED, line -> line.failed()
                ? "Password reset refused" : "Reset their password");
        on(AuthEventType.TOKEN_REUSE_DETECTED,
                line -> "Signed out of a session after it was used twice — a sign of a copied session");
        on(AuthEventType.SESSION_REVOKED, line -> "Signed out a session");
        on(AuthEventType.OTHER_SESSIONS_REVOKED, line -> "Signed out of every other session");
        on(AuthEventType.WORKSPACE_SWITCHED, line -> line.is("reason", "MEMBERSHIP_ENDED")
                ? "Moved into this workspace when access to another ended" : "Switched into this workspace");
        on(AuthEventType.EXTENSION_PAIRED, line -> "Connected the browser extension");
    }

    private void membersAndAccess() {
        on(WorkspaceEventType.WORKSPACE_CREATED, line -> "Created the workspace");
        on(WorkspaceEventType.MEMBER_INVITED, line -> "Invited " + nameOr(line, "someone") + " as "
                + (line.is("type", "client") ? article(contactWord(line)) : roleWord(line, line.text("role"))));
        on(WorkspaceEventType.INVITATION_ACCEPTED, line -> line.failed()
                ? "An invitation was refused: it was sent to another address"
                : "Joined the workspace as " + roleWord(line, line.text("role")));
        on(WorkspaceEventType.INVITATION_REVOKED, line -> "Withdrew the invitation to " + nameOr(line, "someone"));
        on(WorkspaceEventType.MEMBER_ROLE_CHANGED, line -> line.text("addedRole") != null
                ? "Made " + nameOr(line, "a member") + " " + article(contactWord(line)) + " as well"
                : "Changed " + possessive(nameOr(line, "a member")) + " roles from " + rolesWord(line, line.text("from"))
                        + " to " + rolesWord(line, line.text("to")));
        on(WorkspaceEventType.MEMBER_REMOVED, line -> "Removed " + nameOr(line, "a member") + " from the workspace");
        on(ProjectEventType.PROJECT_TEAM_CHANGED, line -> switch (String.valueOf(line.text("action"))) {
            case "add" -> "Added " + nameOr(line, "a member") + " to the position";
            case "roles" -> "Changed " + possessive(nameOr(line, "a member")) + " role on the position";
            case "remove" -> "Removed " + nameOr(line, "a member") + " from the position";
            case "attach-client", "attach-client-accepted" -> "Gave " + nameOr(line, article(contactWord(line)))
                    + " access to the position";
            case "attach-client-pending" -> "Invited " + article(contactWord(line)) + " to the position";
            case "detach-client", "detach-client-pending" -> "Took away " + possessive(nameOr(line,
                    article(contactWord(line)))) + " access to the position";
            default -> "Changed who works on the position";
        });
        on(ProjectEventType.CLIENT_REP_INVITED, line -> "Added " + article(contactWord(line)) + " to "
                + nameOr(line, "a " + unitWord(line)));
        on(ProjectEventType.CLIENT_REP_ACCEPTED, line -> "Accepted access as " + article(contactWord(line))
                + " of " + nameOr(line, "a " + unitWord(line)));
        on(ProjectEventType.CANDIDATE_CLIENT_VISIBILITY_CHANGED, line -> line.is("visible", "true")
                ? "Showed a candidate to " + contactWord(line) + "s" : "Hid a candidate from " + contactWord(line) + "s");
        on(WorkspaceEventType.SEAT_ADDED, line -> billedStaff(line));
        on(WorkspaceEventType.SEAT_REMOVED, line -> billedStaff(line));
    }

    private void exportsAndDownloads() {
        on(ProjectEventType.COMPANIES_EXPORTED, line -> "Exported " + count(line.text("rows"), "company", "companies")
                + stageSuffix(line.text("stage")));
        on(ProjectEventType.CANDIDATES_EXPORTED, line -> "Exported " + count(line.text("people"), "candidate",
                "candidates") + (line.is("selected", "true") ? " (a selection)" : ""));
        on(ProjectEventType.PERSON_DOCUMENT_DOWNLOADED, line -> line.is("preview", "true")
                ? "Previewed a candidate's document" : "Downloaded a candidate's document");
        on(ProjectEventType.OUTREACH_ATTACHMENT_DOWNLOADED, line -> "Downloaded an email attachment");
        on(ProjectEventType.NOTETAKER_RECORDING_DOWNLOADED, line -> "Downloaded a call recording");
        on(WorkspaceEventType.AUDIT_LOG_EXPORTED, line -> "Exported the audit log ("
                + count(line.text("rows"), "event", "events") + ")");
    }

    private void settings() {
        on(WorkspaceEventType.WORKSPACE_UPDATED, line -> switch (String.valueOf(line.text("section"))) {
            case "persona" -> "Updated the company profile";
            case "mode" -> "Changed who the workspace hires for, to " + ("AGENCY".equals(line.text("to"))
                    ? "client companies" : "its own business units");
            case "calendarSync" -> "Changed how calendars are read";
            case "integrations" -> line.text("mode") == null
                    ? "Recorded " + providerName(line.text("provider")) + " admin approval"
                    : "Changed the " + providerName(line.text("provider")) + " app in Integrations";
            case "availability" -> "Changed working hours or the daily email limit";
            case "pipeline" -> "Changed the pipeline stages";
            default -> "Updated the workspace's details";
        });
        on(WorkspaceEventType.WORKSPACE_DELETED, line -> "Deleted the workspace");
        on(WorkspaceEventType.POSITION_TEMPLATE_CREATED, line -> "Added a role template");
        on(WorkspaceEventType.POSITION_TEMPLATE_CUSTOMISED, line -> "Customised a role template");
        on(WorkspaceEventType.POSITION_TEMPLATE_UPDATED, line -> "Edited a role template");
        on(WorkspaceEventType.POSITION_TEMPLATE_RESET, line -> "Reset a role template to the library's");
        on(WorkspaceEventType.POSITION_TEMPLATE_DELETED, line -> "Deleted a role template");
        on(WorkspaceEventType.POSITION_TEMPLATE_HIDDEN, line -> "Hid a role template");
        on(WorkspaceEventType.POSITION_TEMPLATE_SHOWN, line -> "Showed a role template again");
        on(WorkspaceEventType.POSITION_TEMPLATES_IMPORTED, line -> "Imported role templates");
        on(ProjectEventType.CANDIDATE_TAG_CREATED, line -> "Added a candidate tag");
        on(ProjectEventType.CANDIDATE_TAG_UPDATED, line -> "Changed a candidate tag");
        on(ProjectEventType.CANDIDATE_PIPELINE_REORDERED, line -> "Reordered the pipeline");
        on(ProjectEventType.TASK_PRESET_CREATED, line -> "Added a task preset");
        on(ProjectEventType.TASK_PRESET_UPDATED, line -> "Changed a task preset");
        on(ProjectEventType.TASK_PRESETS_REORDERED, line -> "Reordered the task presets");
        on(ProjectEventType.TASK_RULES_CHANGED, line -> "Changed the automatic task rules");
        on(WorkspaceEventType.SUBSCRIPTION_CHANGED, line -> "PAST_DUE".equals(line.text("status"))
                ? "A subscription payment failed" : "Subscription changed"
                + (line.text("plan") == null ? "" : " to the " + titled(line.text("plan")) + " plan"));
        on(WorkspaceEventType.CREDITS_PURCHASED, line -> "Bought " + count(line.text("credits"), "contact credit",
                "contact credits"));
        on(WorkspaceEventType.BILLING_CHECKOUT_STARTED, line -> line.text("plan") != null
                ? "Started checkout for the " + titled(line.text("plan")) + " plan"
                : "Started checkout for " + count(line.text("credits"), "contact credit", "contact credits"));
        on(WorkspaceEventType.BILLING_PORTAL_OPENED, line -> "Opened invoices and card details");
        on(WorkspaceEventType.FAIR_USE_REACHED, line -> "Reached this month's fair-use limit");
        on(PlatformEventType.CREDITS_GRANTED, line -> "Added " + count(line.text("credits"), "contact credit",
                "contact credits"));
        on(PlatformEventType.INVOICED_SUBSCRIPTION_SET, line -> "Set the plan to " + titled(line.text("plan"))
                + " for " + count(line.text("seats"), "staff member", "staff members") + ", invoiced");
    }

    private void apiKeysAndAiApps() {
        on(WorkspaceEventType.API_KEY_CREATED, line -> "Created " + keyKind(line));
        on(WorkspaceEventType.API_KEY_REVOKED, line -> switch (String.valueOf(line.text("reason"))) {
            case "MEMBER_REMOVED" -> "A personal API key ended when its owner left the workspace";
            case "WORKSPACE_DELETED" -> "An API key ended with the workspace";
            default -> "Revoked " + keyKind(line);
        });
        on(WorkspaceEventType.OAUTH_GRANT_CREATED, line -> "Connected an AI app");
        on(WorkspaceEventType.OAUTH_GRANT_REVOKED, line -> switch (String.valueOf(line.text("reason"))) {
            case "CLIENT_REVOKED" -> "An AI app disconnected itself";
            case "REFRESH_REUSE" -> "An AI app was disconnected after its access was used twice";
            case "MEMBER_REMOVED" -> "An AI app was disconnected when its owner left the workspace";
            case "PASSWORD_CHANGED" -> "An AI app was disconnected by a password change";
            case "WORKSPACE_DELETED" -> "An AI app was disconnected with the workspace";
            default -> "Disconnected an AI app";
        });
        on(ProjectEventType.PUBLIC_API_READ, line -> "Read data through " + keyKind(line));
        on(ProjectEventType.MCP_TOOL_CALL, line -> "API_KEY".equals(line.text("credentialKind"))
                ? "Read data through an AI app connected with an API key" : "Read data through a connected AI app");
    }

    private void outreach() {
        on(WorkspaceEventType.MAILBOX_CONNECTED, line -> "Connected their mailbox");
        on(WorkspaceEventType.MAILBOX_DISCONNECTED, line -> "Disconnected their mailbox");
        on(WorkspaceEventType.ZOOM_CONNECTED, line -> "Connected Zoom");
        on(WorkspaceEventType.ZOOM_DISCONNECTED, line -> "Disconnected Zoom");
        on(ProjectEventType.OUTREACH_SEQUENCE_CREATED, line -> "Created a sequence");
        on(ProjectEventType.OUTREACH_SEQUENCE_UPDATED, line -> "Edited a sequence");
        on(ProjectEventType.OUTREACH_SEQUENCE_DELETED, line -> "Deleted a sequence");
        on(ProjectEventType.OUTREACH_ENROLLED, line -> "Added candidates to a sequence");
        on(ProjectEventType.OUTREACH_EMAIL_SENT, line -> "A sequence email was sent");
        on(ProjectEventType.OUTREACH_REPLIED, line -> "A candidate replied");
        on(ProjectEventType.OUTREACH_BOUNCED, line -> "An email bounced");
        on(ProjectEventType.OUTREACH_STOPPED, line -> "A sequence stopped for a candidate");
        on(ProjectEventType.OUTREACH_MESSAGE_SCHEDULED, line -> "Scheduled an email");
        on(ProjectEventType.OUTREACH_MESSAGE_SENT, line -> "Sent an email");
        on(ProjectEventType.OUTREACH_MESSAGE_FAILED, line -> "An email could not be sent");
        on(ProjectEventType.OUTREACH_MESSAGE_CANCELLED, line -> "Cancelled a scheduled email");
        on(ProjectEventType.OUTREACH_MEETING_BOOKED, line -> "Booked a call");
        on(ProjectEventType.OUTREACH_MEETING_RESCHEDULED, line -> "Moved a call");
        on(ProjectEventType.OUTREACH_MEETING_CANCELLED, line -> "Cancelled a call");
        on(ProjectEventType.OUTREACH_OPENERS_DRAFTED, line -> "Drafted email openers with AI");
        on(ProjectEventType.NOTETAKER_SCHEDULED, line -> "Set the Notetaker to join a call");
        on(ProjectEventType.NOTETAKER_CANCELLED, line -> "Removed the Notetaker from a call");
        on(ProjectEventType.NOTETAKER_JOINED_NOW, line -> "Sent the Notetaker into a call");
        on(ProjectEventType.NOTETAKER_ENDED_EARLY, line -> "Ended a call's recording and filed its notes");
        on(ProjectEventType.NOTETAKER_NOTE_FILED, line -> "Filed a call's notes");
        on(ProjectEventType.NOTETAKER_SUGGESTIONS_ASKED, line -> "Asked for changes from a call");
        on(ProjectEventType.NOTETAKER_SUGGESTION_APPLIED, line -> "Applied changes from a call");
        on(ProjectEventType.NOTETAKER_SUGGESTION_SKIPPED, line -> "Skipped changes from a call");
        on(ProjectEventType.INTERVIEW_PLAN_DRAFTED, line -> "Drafted interview questions");
        on(ProjectEventType.INTERVIEW_PLAN_SAVED, line -> "Saved interview questions");
        on(ProjectEventType.NOTETAKER_COPILOT_ENABLED, line -> "Turned on the live copilot for a call");
        on(ProjectEventType.NOTETAKER_COPILOT_DISABLED, line -> "Turned off the live copilot for a call");
        on(ProjectEventType.NOTETAKER_COPILOT_STOPPED, line -> "Stopped the live copilot");
        on(ProjectEventType.NOTETAKER_COPILOT_OPENED, line -> "Opened the live copilot");
    }

    /** Ungrouped types whose own name would put an internal word on screen. */
    private void elsewhere() {
        on(ProjectEventType.TRIAGE_BULK_ADDED, line -> "Added companies in bulk");
        on(ProjectEventType.CANDIDATE_AI_ENRICH_REQUESTED, line -> "Asked AI to suggest a candidate's background");
        on(ProjectEventType.CANDIDATE_CONTACT_LOOKED_UP, line -> "Looked up a candidate's contact details");
        on(ProjectEventType.EXECUTIVE_SOURCING_REQUESTED, line -> "Ran Find executives");
        on(ProjectEventType.EXECUTIVE_SOURCING_COMPLETED, line -> "Find executives finished");
        on(ProjectEventType.PEOPLE_SEARCH_PAGE_FETCHED, line -> "Searched for people");
        on(ProjectEventType.ASSISTANT_ASKED, line -> "Asked the assistant");
        on(ProjectEventType.SPREADSHEET_IMPORTED, line -> "Imported a spreadsheet");
        on(WorkspaceEventType.MAILBOX_TIME_ZONE_CHANGED, line -> "Changed their time zone");
        on(AuthEventType.PROFILE_UPDATED, line -> "Updated their profile");
        on(PlatformEventType.PLATFORM_ROLE_GRANTED, line -> "Gave Uncava platform access");
        on(PlatformEventType.PLATFORM_ROLE_REVOKED, line -> "Removed Uncava platform access");
    }

    private void on(AuditEventType type, Function<AuditLine, String> phrase) {
        phrases.put(type.code(), phrase);
    }

    /** {@code TRIAGE_COMPANY_MOVED} → "Company moved": the screens' words for the code's own. */
    static String humanised(String code) {
        String words = Arrays.stream(code.split("_"))
                .filter(word -> !word.equals("TRIAGE"))
                .map(word -> switch (word) {
                    case "PERSON" -> "candidate";
                    case "PROJECT" -> "position";
                    case "AI" -> "AI";
                    default -> word.toLowerCase(Locale.ROOT);
                })
                .collect(Collectors.joining(" "));
        return words.isEmpty() ? UNKNOWN_ACTIVITY : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static String nameOr(AuditLine line, String fallback) {
        return line.targetName() == null ? fallback : line.targetName();
    }

    private static String contactWord(AuditLine line) {
        return line.agency() ? "client contact" : "hiring manager";
    }

    private static String unitWord(AuditLine line) {
        return line.agency() ? "client" : "business unit";
    }

    private static String roleWord(AuditLine line, String role) {
        if (role == null) {
            return "a member";
        }
        return switch (role) {
            case "ADMIN" -> "an Admin";
            case "MEMBER" -> "a Member";
            case "CLIENT" -> article(contactWord(line));
            default -> "a member";
        };
    }

    private static String rolesWord(AuditLine line, String roles) {
        if (roles == null || roles.isBlank()) {
            return "none";
        }
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .map(role -> switch (role) {
                    case "ADMIN" -> "Admin";
                    case "MEMBER" -> "Member";
                    case "CLIENT" -> titled(contactWord(line));
                    default -> titled(role);
                })
                .collect(Collectors.joining(" and "));
    }

    private static String keyKind(AuditLine line) {
        return "SERVICE".equals(line.text("kind")) ? "a workspace API key" : "a personal API key";
    }

    private static String billedStaff(AuditLine line) {
        return "Billed staff changed from " + orQuestion(line.text("previousSeats")) + " to "
                + orQuestion(line.text("seats"));
    }

    private static String stageSuffix(String stage) {
        if (stage == null) {
            return "";
        }
        return switch (stage) {
            case "inUniverse" -> " from In universe";
            case "shortlisted" -> " from Shortlisted";
            case "declined" -> " from Declined";
            default -> "";
        };
    }

    private static String providerName(String provider) {
        if (provider == null || provider.isBlank()) {
            return "a provider";
        }
        return switch (provider.toLowerCase(Locale.ROOT)) {
            case "linkedin" -> "LinkedIn";
            case "google" -> "Google";
            case "microsoft" -> "Microsoft";
            case "zoom" -> "Zoom";
            default -> titled(provider);
        };
    }

    private static String count(String number, String one, String many) {
        if (number == null) {
            return many;
        }
        return number + " " + ("1".equals(number) ? one : many);
    }

    private static String orQuestion(String value) {
        return value == null ? "?" : value;
    }

    private static String article(String noun) {
        return ("aeiou".indexOf(noun.charAt(0)) >= 0 ? "an " : "a ") + noun;
    }

    private static String possessive(String name) {
        return name.endsWith("s") ? name + "'" : name + "'s";
    }

    private static String titled(String word) {
        if (word == null || word.isBlank()) {
            return "";
        }
        String lower = word.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
