import { request, requestBlob } from "../../../lib/apiClient";

/**
 * The Notetaker: a Recall.ai bot sent into a call with an executive, whose transcript becomes a Meeting note. The
 * position's `WORK_EXECUTE`, like the meetings it records.
 */

export const NOTETAKER_KEY = (projectId: string, candidateId: string) =>
  ["outreach", projectId, "candidate", candidateId, "notetaker"] as const;

export const NOTETAKER_TRANSCRIPT_KEY = (projectId: string, candidateId: string, recordingId: string) =>
  ["outreach", projectId, "candidate", candidateId, "notetaker", recordingId, "transcript"] as const;

export const CALL_SUGGESTIONS_KEY = (projectId: string, candidateId: string, recordingId: string) =>
  ["outreach", projectId, "candidate", candidateId, "notetaker", recordingId, "suggestions"] as const;

export type NotetakerStatus = "SCHEDULED" | "JOINING" | "RECORDING" | "PROCESSING" | "DONE" | "FAILED" | "CANCELLED";

export interface NotetakerRecording {
  id: string;
  /** The meeting row it was sent for; a calendar's re-read can replace that row, so `startsAt` and `title` match too. */
  meetingId: string | null;
  startsAt: string;
  title: string | null;
  status: NotetakerStatus;
  /** Recall's sub-code or our own word, worded by `lib/notetakerChip.ts`. */
  failureReason: string | null;
  hasTranscript: boolean;
  retryable: boolean;
  noteId: string | null;
  /** The transcript was read for changes: once the notes were filed, or by Suggest changes. */
  suggestionsAsked: boolean;
  /** Changes it suggested that nobody has applied or skipped yet. */
  pendingSuggestions: number;
  /** Changes a review applied to the record. */
  appliedSuggestions: number;
  /** Every change it suggested, decided or not. */
  foundSuggestions: number;
  /** The model is reading the transcript for changes right now. */
  suggesting: boolean;
  /** The bot streams the call to the live copilot; chosen before it joins. */
  liveCopilot: boolean;
  liveStatus: LiveCopilotStatus | null;
  /** The caller sent this bot, so may switch its copilot. */
  mayChangeCopilot: boolean;
  /** When Recall brings the bot in; null for one sent at once. */
  joinAt: string | null;
  /** When Recall first reported it recording: the call clock counts from here. */
  recordingStartedAt: string | null;
  /** End & file notes was pressed: the bot is leaving and its notes follow. */
  endRequested: boolean;
  /** When Recall drops the call's recording; null once it is gone, or before the call has ended. */
  recordingAvailableUntil: string | null;
  /** The caller sent this bot and Recall still holds the recording: the only person who may download it. */
  mayDownloadRecording: boolean;
}

/** Start now sends the bot at once; a send naming neither joins at the call's start. */
export type NotetakerJoin = "NOW" | "AT_START";

export interface CandidateNotetaker {
  offered: boolean;
  recordings: NotetakerRecording[];
  /** Upcoming rows the caller may send a bot to: on their own calendar or booked by them, with a video link. */
  recordableMeetingIds: string[];
  /** Of those, the calls whose day has begun in the caller's time zone: Start now is offered on them. */
  startableMeetingIds: string[];
}

export interface NotetakerTranscriptLine {
  at: string;
  speaker: string;
  text: string;
}

export interface NotetakerTranscript {
  recordingId: string;
  title: string | null;
  startsAt: string;
  lines: NotetakerTranscriptLine[];
}

export type CallSuggestionKind =
  | "NOTICE_PERIOD"
  | "CURRENT_PACKAGE"
  | "LOCATION"
  | "OPEN_TO_RELOCATE"
  | "MOTIVATION"
  | "EXPECTED_PACKAGE"
  | "TASK";

export type CallSuggestionStatus = "PENDING" | "APPLIED" | "SKIPPED";

/** Which parts are set follows the kind: `text`, a place, or a package's currency and annual figures. */
export interface SuggestedValue {
  text: string | null;
  city: string | null;
  country: string | null;
  currency: string | null;
  baseSalary: number | null;
  bonus: number | null;
  allowances: number | null;
  /** How monthly figures were annualised, when they were. */
  working: string | null;
}

export interface CallSuggestion {
  id: string;
  kind: CallSuggestionKind;
  status: CallSuggestionStatus;
  value: SuggestedValue;
  /** What the record holds now, read live; null for a task. */
  current: SuggestedValue | null;
  /** The executive's own words, and when in the call they said them. */
  said: string | null;
  at: string | null;
  /** False once the record moved on so it can't be applied, such as the tag being retired. */
  available: boolean;
  edited: boolean;
  /** What an applied change replaced; null where it added rather than replaced, or that wasn't kept. */
  was: SuggestedValue | null;
  appliedAt: string | null;
  appliedBy: string | null;
}

export interface CallSuggestions {
  recordingId: string;
  title: string | null;
  startsAt: string;
  asked: boolean;
  mayAskAgain: boolean;
  suggestions: CallSuggestion[];
}

export type PackageField = "baseSalary" | "bonus" | "allowances";

/** One waiting change a review applies, as suggested or as edited; every change the review leaves out is skipped. */
export interface AppliedCallSuggestion {
  id: string;
  value?: Partial<Omit<SuggestedValue, "working">>;
  packageFields?: PackageField[];
}

function candidatePath(projectId: string, candidateId: string): string {
  return `/projects/${projectId}/outreach/candidates/${candidateId}`;
}

export function getNotetaker(
  projectId: string,
  candidateId: string,
  signal?: AbortSignal,
): Promise<CandidateNotetaker> {
  return request<CandidateNotetaker>(`${candidatePath(projectId, candidateId)}/notetaker`, { signal });
}

export function scheduleNotetaker(
  projectId: string,
  candidateId: string,
  meetingId: string,
  liveCopilot = false,
  join?: NotetakerJoin,
): Promise<NotetakerRecording> {
  return request<NotetakerRecording>(`${candidatePath(projectId, candidateId)}/meetings/${meetingId}/notetaker`, {
    method: "POST",
    body: join ? { liveCopilot, join } : { liveCopilot },
  });
}

/** Brings a bot scheduled for the call's start in at once. */
export function joinNotetakerNow(
  projectId: string,
  candidateId: string,
  recordingId: string,
): Promise<NotetakerRecording> {
  return request<NotetakerRecording>(`${candidatePath(projectId, candidateId)}/notetaker/${recordingId}/join-now`, {
    method: "POST",
  });
}

/** End & file notes: the bot leaves the call now, and its notes are filed as after any call. */
export function endNotetaker(projectId: string, candidateId: string, recordingId: string): Promise<NotetakerRecording> {
  return request<NotetakerRecording>(`${candidatePath(projectId, candidateId)}/notetaker/${recordingId}/end`, {
    method: "POST",
  });
}

export function cancelNotetaker(projectId: string, candidateId: string, recordingId: string): Promise<void> {
  return request<void>(`${candidatePath(projectId, candidateId)}/notetaker/${recordingId}`, { method: "DELETE" });
}

export function retryNotetaker(
  projectId: string,
  candidateId: string,
  recordingId: string,
): Promise<NotetakerRecording> {
  return request<NotetakerRecording>(`${candidatePath(projectId, candidateId)}/notetaker/${recordingId}/retry`, {
    method: "POST",
  });
}

/** The call's video, fetched through the server (Recall's link never reaches the browser); the bytes are held in memory. */
export function downloadRecording(projectId: string, candidateId: string, recordingId: string): Promise<Blob> {
  return requestBlob(`${candidatePath(projectId, candidateId)}/notetaker/${recordingId}/recording`);
}

export function getTranscript(
  projectId: string,
  candidateId: string,
  recordingId: string,
  signal?: AbortSignal,
): Promise<NotetakerTranscript> {
  return request<NotetakerTranscript>(`${candidatePath(projectId, candidateId)}/notetaker/${recordingId}/transcript`, {
    signal,
  });
}

function suggestionsPath(projectId: string, candidateId: string, recordingId: string): string {
  return `${candidatePath(projectId, candidateId)}/notetaker/${recordingId}/suggestions`;
}

export function getCallSuggestions(
  projectId: string,
  candidateId: string,
  recordingId: string,
  signal?: AbortSignal,
): Promise<CallSuggestions> {
  return request<CallSuggestions>(suggestionsPath(projectId, candidateId, recordingId), { signal });
}

/** Spends a model call: only ever on the consultant's press. */
export function suggestChanges(projectId: string, candidateId: string, recordingId: string): Promise<CallSuggestions> {
  return request<CallSuggestions>(suggestionsPath(projectId, candidateId, recordingId), { method: "POST" });
}

/** One save for the whole review: what is listed is applied, the rest skipped, and nothing is undone afterwards. */
export function reviewCallSuggestions(
  projectId: string,
  candidateId: string,
  recordingId: string,
  apply: AppliedCallSuggestion[],
): Promise<CallSuggestions> {
  return request<CallSuggestions>(`${suggestionsPath(projectId, candidateId, recordingId)}/review`, {
    method: "POST",
    body: { apply },
  });
}

/* ── Interview copilot: questions prepared for a call, and the live copilot that follows it ── */

export type LiveCopilotStatus = "LISTENING" | "FEED_STOPPED" | "STOPPED";
export type CoachState = "RUNNING" | "PAUSED" | "GUARDED" | "CAPPED";
export type CoverageState = "NOT_YET" | "TOUCHED" | "EVIDENCED";
export type RecordGap = "NOTICE" | "PACKAGE" | "LOCATION" | "RELOCATION" | "MOTIVATION" | "EXPECTED_PACKAGE";
export type InterviewQuestionSection = "FIT" | "GAP" | "CLARIFY";
export type CompetencyPanel = "TECHNICAL" | "BEHAVIOURAL";

export const INTERVIEW_PLAN_KEY = (projectId: string, candidateId: string, meetingId: string) =>
  ["outreach", projectId, "candidate", candidateId, "interview-plan", meetingId] as const;

/** The draft of one call's plan, whichever dialog started it. */
export const INTERVIEW_PLAN_DRAFT_KEY = (projectId: string, candidateId: string, meetingId: string) =>
  [...INTERVIEW_PLAN_KEY(projectId, candidateId, meetingId), "draft"] as const;

export const COPILOT_KEY = (projectId: string, candidateId: string, recordingId: string) =>
  ["outreach", projectId, "candidate", candidateId, "notetaker", recordingId, "copilot"] as const;

export interface InterviewQuestion {
  /** Null on a question added in the dialog and not yet saved. */
  id: string | null;
  section: InterviewQuestionSection;
  panel: CompetencyPanel | null;
  competency: string | null;
  gap: RecordGap | null;
  text: string;
  listenFor: string | null;
  why: string | null;
  included: boolean;
  source: "AI" | "MANUAL" | null;
}

export interface PlanCompetency {
  panel: CompetencyPanel;
  name: string;
  weight: number;
  /** The scorecard already shows it: a question confirms rather than proves. */
  evidenced: boolean;
}

export interface InterviewPlan {
  meetingId: string;
  title: string | null;
  startsAt: string;
  drafted: boolean;
  savedAt: string | null;
  savedByName: string | null;
  competencies: PlanCompetency[];
  /** What the record lacks today, which the gap questions are for. */
  missing: RecordGap[];
  questions: InterviewQuestion[];
}

export interface CopilotCoverage {
  panel: CompetencyPanel;
  competency: string;
  weight: number;
  state: CoverageState;
  quote: string | null;
  at: string | null;
}

export interface CopilotLine {
  seq: number;
  at: string;
  speaker: string;
  text: string;
  /** The consultant's or a colleague's voice. */
  team: boolean;
}

export interface Copilot {
  recordingId: string;
  title: string | null;
  candidateName: string;
  startsAt: string;
  /** When Recall first reported recording; the call clock counts from here, or from `startsAt` before. */
  recordingStartedAt: string | null;
  status: NotetakerStatus;
  liveStatus: LiveCopilotStatus | null;
  coach: CoachState;
  stoppedByName: string | null;
  mayStop: boolean;
  coverage: CopilotCoverage[];
  gaps: { gap: RecordGap; heard: boolean; at: string | null }[];
  plan: { id: string; section: InterviewQuestionSection; competency: string | null; text: string; asked: boolean }[];
  nudges: { id: string; text: string; reason: string | null }[];
  /** Finished lines after the cursor sent; `lastSeq` is the next cursor. */
  lines: CopilotLine[];
  /** Each voice's words so far. */
  speaking: CopilotLine[];
  lastSeq: number;
  viewers: { userId: string; name: string | null; avatarUrl: string | null; you: boolean }[];
}

function planPath(projectId: string, candidateId: string, meetingId: string): string {
  return `${candidatePath(projectId, candidateId)}/meetings/${meetingId}/plan`;
}

export function getInterviewPlan(
  projectId: string,
  candidateId: string,
  meetingId: string,
  signal?: AbortSignal,
): Promise<InterviewPlan> {
  return request<InterviewPlan>(planPath(projectId, candidateId, meetingId), { signal });
}

/** Spends a model call: only ever on the consultant's press, or on opening a plan never drafted. */
export function draftInterviewPlan(projectId: string, candidateId: string, meetingId: string): Promise<InterviewPlan> {
  return request<InterviewPlan>(planPath(projectId, candidateId, meetingId), { method: "POST" });
}

export function saveInterviewPlan(
  projectId: string,
  candidateId: string,
  meetingId: string,
  questions: InterviewQuestion[],
): Promise<InterviewPlan> {
  return request<InterviewPlan>(planPath(projectId, candidateId, meetingId), {
    method: "PUT",
    body: { questions },
  });
}

export function copilotPath(projectId: string, candidateId: string, recordingId: string): string {
  return `${candidatePath(projectId, candidateId)}/notetaker/${recordingId}/copilot`;
}

export function switchLiveCopilot(
  projectId: string,
  candidateId: string,
  recordingId: string,
  enabled: boolean,
): Promise<NotetakerRecording> {
  return request<NotetakerRecording>(copilotPath(projectId, candidateId, recordingId), {
    method: "PUT",
    body: { enabled },
  });
}

export function getCopilot(
  projectId: string,
  candidateId: string,
  recordingId: string,
  after: number,
  signal?: AbortSignal,
): Promise<Copilot> {
  return request<Copilot>(`${copilotPath(projectId, candidateId, recordingId)}?after=${after}`, { signal });
}

export function stopCopilot(projectId: string, candidateId: string, recordingId: string): Promise<Copilot> {
  return request<Copilot>(`${copilotPath(projectId, candidateId, recordingId)}/stop`, { method: "POST" });
}

export function decideNudge(
  projectId: string,
  candidateId: string,
  recordingId: string,
  nudgeId: string,
  decision: "used" | "dismiss",
): Promise<void> {
  return request<void>(`${copilotPath(projectId, candidateId, recordingId)}/nudges/${nudgeId}/${decision}`, {
    method: "POST",
  });
}

export function markPlanQuestionAsked(
  projectId: string,
  candidateId: string,
  recordingId: string,
  questionId: string,
  asked: boolean,
): Promise<void> {
  return request<void>(`${copilotPath(projectId, candidateId, recordingId)}/plan/${questionId}`, {
    method: "PUT",
    body: { asked },
  });
}
