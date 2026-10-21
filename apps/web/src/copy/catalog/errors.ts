import { defineScreen } from "../defineScreen";

/**
 * Every refusal the API can answer, worded for the person who hit it, grouped the way the screens are. `messageFor` reads
 * them by code; the server's own sentence is shown only for the codes `DETAIL_ALLOWED` names.
 */

export const errorsGeneral = defineScreen("errors-general", "Error messages · Everywhere", {
  generic: { text: "Something went wrong. Try again.", where: "Any failure with no reference to quote", kind: "error" },
  unexpected: {
    text: "Something went wrong on our side. Try again, or contact support and quote reference {reference}.",
    where: "Any failure we did not expect; {reference} is the request's support reference",
    kind: "error",
  },
  unsavedChanges: {
    text: "Couldn't save your last change. Check your connection and try again.",
    where: "An action that first had to save the screen's edits, when that save was refused",
    kind: "error",
  },
  FORBIDDEN: { text: "You don't have permission to do this.", where: "Shown when: forbidden", kind: "error" },
  RATE_LIMITED: { text: "Too many requests in a short time. Wait a minute and try again.", where: "Shown when: rate limited", kind: "error" },
  NOT_FOUND: { text: "That item no longer exists. Reload to see the latest.", where: "Shown when: not found", kind: "error" },
});

export const errorsAccount = defineScreen("errors-account", "Error messages · Account and workspace", {
  LAST_ADMIN: { text: "A workspace must keep at least one admin.", where: "Shown when: last admin", kind: "error" },
  MEMBER_LEADS_PROJECTS: { text: "They are the only lead on active positions — hand those over first.", where: "Shown when: member leads projects", kind: "error" },
  WORKSPACE_NAME_MISMATCH: { text: "Type the workspace name exactly to confirm.", where: "Shown when: workspace name mismatch", kind: "error" },
  ALREADY_IN_WORKSPACE: { text: "You already belong to a workspace. Found another from Settings → Workspaces.", where: "Shown when: already in workspace", kind: "error" },
  EMAIL_NOT_VERIFIED: { text: "Verify your email address to continue.", where: "Shown when: email not verified", kind: "error" },
  ACCOUNT_SUSPENDED: { text: "This account has been suspended.", where: "Shown when: account suspended", kind: "error" },
  EMAIL_NOT_WORK_ADDRESS: { text: "Use your work email — the domain identifies your organization.", where: "Shown when: email not work address", kind: "error" },
  CURRENT_PASSWORD_INVALID: { text: "That is not your current password.", where: "Shown when: current password invalid", kind: "error" },
  PASSWORD_NOT_SET: { text: "This account signs in with a provider — set a password from the reset link.", where: "Shown when: password not set", kind: "error" },
  SESSION_NOT_FOUND: { text: "That session has already ended.", where: "Shown when: session not found", kind: "error" },
  CURRENT_SESSION_NOT_REVOCABLE: { text: "Use sign out to end the session you are using.", where: "Shown when: current session not revocable", kind: "error" },
  WORKSPACE_NOT_FOUND: { text: "That workspace isn't available to you any more.", where: "Shown when: workspace not found", kind: "error" },
  NOT_A_MEMBER: { text: "That workspace isn't available to you any more.", where: "Shown when: not a member", kind: "error" },
});

export const errorsPositions = defineScreen("errors-positions", "Error messages · Positions and the market", {
  ASSISTANT_UNAVAILABLE: { text: "The assistant could not answer just now. Try again in a moment.", where: "Shown when: assistant unavailable", kind: "error" },
  ASSISTANT_BUSY: { text: "The assistant is busy answering other questions. Try again in a moment.", where: "Shown when: assistant busy", kind: "error" },
  ASSISTANT_STILL_ANSWERING: { text: "This is taking longer than usual. The answer will appear here when it is ready — no need to ask again.", where: "Shown when: assistant still answering", kind: "error" },
  ASSISTANT_PROPOSAL_ALREADY_ACCEPTED: { text: "These companies have already been filed.", where: "Shown when: assistant proposal already accepted", kind: "error" },
  TEMPLATE_STALE: { text: "Someone saved this template after you opened it. Reload to see their version.", where: "Shown when: template stale", kind: "error" },
  TEMPLATE_FALLBACK_REQUIRED: { text: "The fallback template can't be archived or hidden — a title nothing else matches is drafted from it.", where: "Shown when: template fallback required", kind: "error" },
  TEMPLATE_IMPORT_INVALID: { text: "Some templates in the file are invalid, so none were imported.", where: "Shown when: template import invalid", kind: "error" },
  CLIENT_ALREADY_EXISTS: { text: "That name is already taken.", where: "Shown when: client already exists", kind: "error" },
  PROJECT_LAST_LEAD: { text: "A position must keep at least one lead.", where: "Shown when: project last lead", kind: "error" },
  ARCHIVE_NAME_MISMATCH: { text: "Type the name exactly as shown to confirm.", where: "Shown when: archive name mismatch", kind: "error" },
  PROJECT_ARCHIVED: { text: "This position is archived. Restore it to start outreach.", where: "Shown when: project archived", kind: "error" },
  CLIENT_HAS_LIVE_POSITIONS: { text: "Some of its positions are still open. Their leads archive them first.", where: "Shown when: client has live positions", kind: "error" },
  CLIENT_ARCHIVED: { text: "That one is archived. Restore it before opening a position under it.", where: "Shown when: client archived", kind: "error" },
  CLIENT_NAME_ARCHIVED: { text: "An archived record already has that name. Restore it instead.", where: "Shown when: client name archived", kind: "error" },
  STRATEGY_SEARCH_NAME_TAKEN: { text: "A search with that name is already saved here.", where: "Shown when: strategy search name taken", kind: "error" },
  // Two uploads raise this — the spreadsheet import and the position description — so the wording
  // stays neutral. Naming one screen's file types here misdescribes the other's refusal, and each
  // dropzone already states what it takes.
  UNSUPPORTED_FILE_TYPE: { text: "That file type is not supported.", where: "Shown when: unsupported file type", kind: "error" },
  CUSTOM_COLUMN_NAME_TAKEN: { text: "This position already has a column with that name. Map onto that column instead.", where: "Shown when: custom column name taken", kind: "error" },
  TRIAGE_COMPANY_NOT_EDITABLE: { text: "This company came from the market export, so its details are not yours to edit.", where: "Shown when: triage company not editable", kind: "error" },
  EXECUTIVE_SOURCING_UNAVAILABLE: { text: "Find executives isn't switched on for your workspace. Contact Uncava support to turn it on.", where: "Shown when: executive sourcing unavailable", kind: "error" },
  EXECUTIVE_SOURCING_TOO_MANY_COMPANIES: { text: "Too many companies ticked. Find executives takes a limited batch — untick some and try again.", where: "Shown when: executive sourcing too many companies", kind: "error" },
  EXECUTIVE_SOURCING_IN_PROGRESS: { text: "A Find executives run is already in progress for this position.", where: "Shown when: executive sourcing in progress", kind: "error" },
  PEOPLE_SEARCH_UNAVAILABLE: { text: "People search isn't switched on for your workspace. Contact Uncava support to turn it on.", where: "Shown when: people search unavailable", kind: "error" },
  // Nothing was bought, so the same button works once the account is topped up.
  PEOPLE_SEARCH_NO_CREDITS: { text: "Your workspace has used this period's people search credits. Ask an admin to top them up.", where: "Shown when: people search no credits", kind: "error" },
  PEOPLE_SEARCH_FAILED: { text: "People search didn't respond — the provider may be busy. Try again in a minute.", where: "Shown when: people search failed", kind: "error" },
  PEOPLE_SEARCH_REJECTED: { text: "People search couldn't run that filter. Loosen or change it and try again.", where: "Shown when: people search rejected", kind: "error" },
  PEOPLE_SEARCH_EMPTY_FILTER: { text: "Add at least one filter before searching.", where: "Shown when: people search empty filter", kind: "error" },
  PEOPLE_SEARCH_PERSON_UNKNOWN: { text: "That person is no longer in the results. Search again and add them from there.", where: "Shown when: people search person unknown", kind: "error" },
  TRIAGE_COMPANY_ALREADY_HELD: { text: "This position already has a company with that name.", where: "Shown when: triage company already held", kind: "error" },
  IMPORT_FILE_UNREADABLE: { text: "That file couldn't be read as a table. Check it has a header row, then try again.", where: "Shown when: import file unreadable", kind: "error" },
});

export const errorsCandidates = defineScreen("errors-candidates", "Error messages · Candidates and pipeline", {
  CANDIDATE_ALREADY_MAPPED: { text: "Someone with that name is already mapped here.", where: "Shown when: candidate already mapped", kind: "error" },
  CANDIDATE_POSSIBLE_DUPLICATE: { text: "Your team already has someone with that name at that employer.", where: "Shown when: candidate possible duplicate", kind: "error" },
  CANDIDATE_KEYS_NAME_ANOTHER: { text: "The LinkedIn profile or email typed belongs to someone else in your candidates.", where: "Shown when: candidate keys name another", kind: "error" },
  PERSON_PROFILE_HELD: { text: "Another candidate in this workspace already has that LinkedIn profile.", where: "Shown when: person profile held", kind: "error" },
  PERSON_NOTE_NOT_YOURS: { text: "Only the person who wrote this note, or an admin, can change it.", where: "Shown when: person note not yours", kind: "error" },
  COMPANY_NOTE_NOT_YOURS: { text: "Only the person who wrote this note, or an admin, can change it.", where: "Shown when: company note not yours", kind: "error" },
  PERSON_DOCUMENT_DUPLICATE: { text: "That file is already on this candidate.", where: "Shown when: person document duplicate", kind: "error" },
  PERSON_DOCUMENT_NOT_YOURS: { text: "Only the person who uploaded this, or an admin, can remove it.", where: "Shown when: person document not yours", kind: "error" },
  PERSON_DOCUMENT_LIMIT: { text: "This candidate has reached the limit for documents.", where: "Shown when: person document limit", kind: "error" },
  TASK_ASSIGNEE_NOT_ON_TEAM: { text: "They aren't on this position's team. Add them in Team & access, or pick someone who is.", where: "Shown when: task assignee not on team", kind: "error" },
  TASK_NOT_YOURS: { text: "Only whoever set this task, whoever it's assigned to, the position's lead or an admin can change it.", where: "Shown when: task not yours", kind: "error" },
  CANDIDATE_TAG_EXISTS: { text: "Your team already has that tag.", where: "Shown when: candidate tag exists", kind: "error" },
  CANDIDATE_TAG_RETIRED: { text: "That tag is retired. Restore it in Settings to use it again.", where: "Shown when: candidate tag retired", kind: "error" },
  CANDIDATE_ASSESSMENT_STALE: { text: "Someone saved this assessment after you opened it. Reload to keep your changes over theirs.", where: "Shown when: candidate assessment stale", kind: "error" },
  CANDIDATE_ASSESSMENT_NO_CRITERIA: { text: "The brief has no competencies to assess against yet. Add them in the position's Assessment Criteria step.", where: "Shown when: candidate assessment no criteria", kind: "error" },
  CANDIDATE_ASSESSMENT_NOTHING_OPEN: { text: "Every score and section is already your team's. There is nothing left for the AI to draft.", where: "Shown when: candidate assessment nothing open", kind: "error" },
  AI_UNAVAILABLE: { text: "The AI could not answer just now. Try again in a moment.", where: "Shown when: ai unavailable", kind: "error" },
  PIPELINE_STAGE_NOT_EMPTY: { text: "This stage still has statuses. Retire them with the stage, or retire them first.", where: "Shown when: pipeline stage not empty", kind: "error" },
  PIPELINE_STAGE_REQUIRED: { text: "The pipeline keeps at least one active stage and its closed stage.", where: "Shown when: pipeline stage required", kind: "error" },
  PIPELINE_STATUS_REQUIRED: { text: "The pipeline keeps at least one active status for new candidates to start at.", where: "Shown when: pipeline status required", kind: "error" },
  PIPELINE_LABEL_TAKEN: { text: "That name is already in use.", where: "Shown when: pipeline label taken", kind: "error" },
  PIPELINE_STATUS_RETIRED: { text: "That status is retired. Ask an admin to restore it in Settings.", where: "Shown when: pipeline status retired", kind: "error" },
  PIPELINE_ORDER_INVALID: { text: "The pipeline changed meanwhile. Reload and try again.", where: "Shown when: pipeline order invalid", kind: "error" },
  PERSON_OWNER_NOT_STAFF: { text: "The owner must be someone on your team.", where: "Shown when: person owner not staff", kind: "error" },
  PERSON_DO_NOT_CONTACT: { text: "This person is marked do not contact, so contact lookups are off.", where: "Shown when: person do not contact", kind: "error" },
  CONTACT_LOOKUP_UNAVAILABLE: { text: "Contact lookup isn't switched on for your workspace. Contact Uncava support to turn it on.", where: "Shown when: contact lookup unavailable", kind: "error" },
  // Distinct from a failure on purpose: nothing was written, so the same button works once the
  // account is topped up.
  CONTACT_LOOKUP_NO_CREDITS: { text: "Your workspace has used this period's contact lookup credits. Ask an admin to top them up.", where: "Shown when: contact lookup no credits", kind: "error" },
  CONTACT_LOOKUP_FAILED: { text: "Contact lookup didn't respond — the provider may be busy. Try again in a minute.", where: "Shown when: contact lookup failed", kind: "error" },
  CONTACT_LOOKUP_NO_PROFILE: { text: "Add this person's LinkedIn profile URL first.", where: "Shown when: contact lookup no profile", kind: "error" },
  CONTACT_LIMIT_REACHED: { text: "A profile holds ten email addresses and ten phone numbers at most.", where: "Shown when: contact limit reached", kind: "error" },
  CANDIDATE_PROFILE_URL_LOCKED: { text: "This profile was captured from LinkedIn, so its URL is not editable.", where: "Shown when: candidate profile url locked", kind: "error" },
});

export const errorsOutreach = defineScreen("errors-outreach", "Error messages · Outreach, mailbox and meetings", {
  MAILBOX_UNAVAILABLE: { text: "Outreach email isn't switched on for your workspace. Contact Uncava support to turn it on.", where: "Shown when: mailbox unavailable", kind: "error" },
  ZOOM_UNAVAILABLE: { text: "Zoom isn't set up for your workspace. Ask an admin to set it up in Settings → Integrations.", where: "Shown when: zoom unavailable", kind: "error" },
  ZOOM_NOT_CONNECTED: { text: "Connect Zoom first.", where: "Shown when: zoom not connected", kind: "error" },
  ZOOM_RECONNECT_NEEDED: { text: "Your Zoom account needs reconnecting.", where: "Shown when: zoom reconnect needed", kind: "error" },
  ZOOM_CONNECT_CANCELLED: { text: "Zoom was not connected.", where: "Shown when: zoom connect cancelled", kind: "error" },
  ZOOM_CONNECT_FAILED: { text: "Zoom could not be connected. Try again.", where: "Shown when: zoom connect failed", kind: "error" },
  INTEGRATION_ENCRYPTION_UNAVAILABLE: { text: "Your own app's keys can't be stored for your workspace yet. Use the shared app for now, or contact Uncava support.", where: "Shown when: integration encryption unavailable", kind: "error" },
  INTEGRATION_SHARED_APP_UNAVAILABLE: { text: "Uncava's shared app isn't available for this provider yet. Use your own app instead.", where: "Shown when: integration shared app unavailable", kind: "error" },
  MAILBOX_PROVIDER_UNSUPPORTED: { text: "That kind of mailbox can't be connected.", where: "Shown when: mailbox provider unsupported", kind: "error" },
  MAILBOX_NOT_CONNECTED: { text: "Connect your mailbox first.", where: "Shown when: mailbox not connected", kind: "error" },
  MAILBOX_RECONNECT_NEEDED: { text: "Your mailbox needs reconnecting before it can send.", where: "Shown when: mailbox reconnect needed", kind: "error" },
  MAILBOX_CONNECT_EXPIRED: { text: "That connection attempt expired. Start again.", where: "Shown when: mailbox connect expired", kind: "error" },
  MAILBOX_CONNECT_FAILED: { text: "Your mailbox couldn't be connected. Try again.", where: "Shown when: mailbox connect failed", kind: "error" },
  MAILBOX_SEND_FAILED: { text: "The email couldn't be sent. Try again in a moment.", where: "Shown when: mailbox send failed", kind: "error" },
  OUTREACH_SEQUENCE_IN_USE: { text: "People are on this sequence, so it can't be deleted.", where: "Shown when: outreach sequence in use", kind: "error" },
  OUTREACH_PERSON_SKIPPED: { text: "Someone you chose can no longer be added. Go back and review the list.", where: "Shown when: outreach person skipped", kind: "error" },
  OUTREACH_ALREADY_ENROLLED: { text: "Someone you chose is already in a sequence on this position.", where: "Shown when: outreach already enrolled", kind: "error" },
  OUTREACH_ADDRESS_NOT_ON_FILE: { text: "That address isn't on file for this person.", where: "Shown when: outreach address not on file", kind: "error" },
  OUTREACH_NOT_RUNNING: { text: "This sequence has already ended.", where: "Shown when: outreach not running", kind: "error" },
  MEETING_DO_NOT_CONTACT: { text: "This person is marked do not contact.", where: "Shown when: meeting do not contact", kind: "error" },
  MEETING_SLOT_TAKEN: { text: "That time is no longer free. Pick another.", where: "Shown when: meeting slot taken", kind: "error" },
  MEETING_SLOT_INVALID: { text: "That time can't be booked. Pick one from the grid.", where: "Shown when: meeting slot invalid", kind: "error" },
  MEETING_BOOK_FAILED: { text: "The invite couldn't be sent. Check your calendar before trying again.", where: "Shown when: meeting book failed", kind: "error" },
  MEETING_CALENDAR_UNAVAILABLE: { text: "Your calendar couldn't be read. Try again in a moment, or reconnect your mailbox if it keeps failing.", where: "Shown when: meeting calendar unavailable", kind: "error" },
  MAILBOX_CONNECT_CANCELLED: { text: "Your mailbox was not connected.", where: "Shown when: mailbox connect cancelled", kind: "error" },
  OUTREACH_BOOKING_LINK_UNAVAILABLE: { text: "Booking links aren't available for your mailbox. Remove the booking link from the emails to start this sequence.", where: "Shown when: outreach booking link unavailable", kind: "error" },
  OUTREACH_BOOKING_LINK_FAILED: { text: "Your booking page couldn't be set up, so nothing was sent. Try again in a moment.", where: "Shown when: outreach booking link failed", kind: "error" },
});

export const errorsBilling = defineScreen("errors-billing", "Error messages · Billing and credits", {
  INSUFFICIENT_CREDITS: { text: "Your workspace is out of contact credits. Nothing was spent.", where: "Shown when: insufficient credits", kind: "error" },
  FAIR_USE_REACHED: { text: "Your team has reached this month's fair use for this feature.", where: "Shown when: fair use reached", kind: "error" },
  TRIAL_ENDED: { text: "Your workspace's trial has ended. Choose a plan to carry on.", where: "Shown when: trial ended", kind: "error" },
  BILLING_UNAVAILABLE: { text: "Paying online isn't available right now. Use Contact Uncava in Settings → Billing instead.", where: "Shown when: billing unavailable", kind: "error" },
  BILLING_PLAN_UNKNOWN: { text: "That plan can't be bought online. Use Contact Uncava in Settings → Billing to choose it.", where: "Shown when: billing plan unknown", kind: "error" },
  BILLING_PACK_UNKNOWN: { text: "That credit pack is no longer sold. Choose another.", where: "Shown when: billing pack unknown", kind: "error" },
  SUBSCRIPTION_BILLED_BY_STRIPE: { text: "This workspace already pays through Stripe — change the plan from Invoices & card.", where: "Shown when: subscription billed by stripe", kind: "error" },
  SEAT_LIMIT_REACHED: { text: "Every seat your workspace pays for is taken. An admin can add seats through Contact Uncava in Settings → Billing.", where: "Shown when: seat limit reached", kind: "error" },
});

export const errorsSettings = defineScreen("errors-settings", "Error messages · Settings", {
  API_KEY_NOT_FOUND: { text: "That key no longer exists. Reload to see the current list.", where: "Shown when: api key not found", kind: "error" },
  API_KEY_LIMIT_REACHED: { text: "You have the most API keys allowed. Revoke one you no longer use.", where: "Shown when: api key limit reached", kind: "error" },
  API_KEY_READS_NOTHING: { text: "Choose what the key may read as well as MCP access.", where: "Shown when: api key reads nothing", kind: "error" },
  OAUTH_GRANT_NOT_FOUND: { text: "That app is already disconnected. Reload to see the current list.", where: "Shown when: oauth grant not found", kind: "error" },
  TASK_PRESET_EXISTS: { text: "Your team already has that preset.", where: "Shown when: task preset exists", kind: "error" },
  SCREEN_TEXT_STALE: { text: "Someone changed this text after you opened it. Reload to see their version.", where: "Shown when: screen text stale", kind: "error" },
  SCREEN_TEXT_KEY_UNKNOWN: { text: "That piece of screen text no longer exists. Reload to see the current list.", where: "Shown when: screen text key unknown", kind: "error" },
  SCREEN_TEXT_NOTHING_TO_PUBLISH: { text: "There are no unpublished changes to publish.", where: "Shown when: screen text nothing to publish", kind: "error" },
});

export const ERROR_SCREENS = [errorsGeneral, errorsAccount, errorsPositions, errorsCandidates, errorsOutreach, errorsBilling, errorsSettings] as const;
