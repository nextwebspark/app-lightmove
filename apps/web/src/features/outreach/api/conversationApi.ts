import { request } from "../../../lib/apiClient";
import type {
  CandidateEmail,
  CandidatePhone,
  PersonNote,
  PersonTimelineEntry,
} from "../../candidates/api/types";
import type { PipelineRow } from "../../pipeline/api/types";
import type { TaskKind, TaskPerson, TaskRule } from "../../tasks/api/types";
import type { Meeting } from "./meetingApi";
import type { CandidateOutreach, OutreachRun, StopReason } from "./runApi";

/**
 * The Outreach page's conversation workspace (docs/outreach-conversations.md): the action queue, one executive's
 * conversation on the position, and a consultant's own email. All the position's `WORK_EXECUTE`.
 */

export type ConversationView = "needs" | "all" | "scheduled" | "sent";
export type ConversationScope = "mine" | "team";
export type ConversationGroup = "NEEDS_REPLY" | "FOLLOW_UP" | "PREPARATION" | "ATTENTION";

export type MessageDirection = "OUTBOUND" | "INBOUND";
export type MessageChannel = "EMAIL" | "WHATSAPP" | "SMS" | "CALL";
export type MessageKind = "SEQUENCE_STEP" | "MANUAL" | "REPLY" | "BOUNCE";
export type MessageStatus = "SCHEDULED" | "SENT" | "FAILED" | "CANCELLED" | "RECEIVED";

export const CONVERSATIONS_KEY_PREFIX = (projectId: string) => ["outreach", projectId, "conversations"] as const;
export const CONVERSATIONS_KEY = (projectId: string, view: ConversationView, who: ConversationScope, query: string) =>
  [...CONVERSATIONS_KEY_PREFIX(projectId), view, who, query] as const;
export const CONVERSATION_KEY = (projectId: string, candidateId: string) =>
  ["outreach", projectId, "conversation", candidateId] as const;

/** An open task as the queue and the conversation name it: what to do, by when, and who holds it. */
export interface ConversationTask {
  taskId: string;
  title: string;
  kind: TaskKind;
  /** The outreach event that filed it; null for one a person set. */
  rule: TaskRule | null;
  dueAt: string | null;
  assignee: TaskPerson;
}

export interface ConversationMessageSummary {
  channel: MessageChannel;
  direction: MessageDirection;
  kind: MessageKind;
  at: string;
  /** Plain words, at most 140 characters; the subject where a line has no text. */
  preview: string | null;
}

export interface ConversationRow {
  candidateId: string;
  personId: string;
  fullName: string;
  title: string | null;
  companyName: string | null;
  enrichedAt: string | null;
  status: string;
  statusLabel: string;
  statusColour: string;
  /** Null outside Needs action, or with nothing open. */
  group: ConversationGroup | null;
  action: ConversationTask | null;
  run: OutreachRun | null;
  latest: ConversationMessageSummary | null;
  lastSentAt: string | null;
  scheduledAt: string | null;
  lastActivityAt: string | null;
}

export interface ConversationList {
  /** Read for the caller's Mine or Team, before the search narrows anything. */
  counts: { needs: number; scheduled: number };
  nextScheduledAt: string | null;
  rows: ConversationRow[];
  truncated: boolean;
}

export interface ConversationMessage {
  id: string;
  direction: MessageDirection;
  channel: MessageChannel;
  kind: MessageKind;
  status: MessageStatus;
  /** When it went or came back; when it is due for one still waiting. */
  at: string;
  subject: string | null;
  /** Drawn as text, or as a note's Markdown — never as HTML. */
  text: string | null;
  format: "TEXT" | "MARKDOWN";
  fromAddress: string | null;
  toAddress: string | null;
  senderUserId: string;
  senderName: string | null;
  enrollmentId: string | null;
  sequenceName: string | null;
  stepNumber: number | null;
  stepCount: number | null;
  failReason: StopReason | null;
}

export type ConversationEvent =
  | { type: "MESSAGE"; at: string; message: ConversationMessage }
  | { type: "ACTIVITY"; at: string; activity: PersonTimelineEntry }
  | { type: "NOTE"; at: string; note: PersonNote }
  | { type: "MEETING"; at: string; meeting: Meeting };

export interface ConversationContact {
  emails: CandidateEmail[];
  phones: CandidatePhone[];
  locationCity: string | null;
  locationCountry: string | null;
  doNotContact: boolean;
}

export interface StatusTarget {
  statusKey: string;
  label: string;
  colour: string;
}

export interface ConversationComposer {
  /** The subject a reply from the caller's mailbox would carry; null where they have no thread to reply in. */
  replySubject: string | null;
  /** Where an email now would move the person; null where they would stay. */
  emailSentMovesTo: StatusTarget | null;
}

export interface Conversation {
  executive: PipelineRow;
  openTasks: ConversationTask[];
  currentRun: CandidateOutreach;
  keyDates: {
    addedAt: string | null;
    firstContactedAt: string | null;
    repliedAt: string | null;
    lastActivityAt: string | null;
  };
  contact: ConversationContact;
  composer: ConversationComposer;
  /** Newest first. */
  events: ConversationEvent[];
  /** The history holds more than one read returns; the oldest are left off. */
  truncated: boolean;
}

export interface SendMessageRequest {
  toAddress: string;
  /** A first email's; a reply keeps its thread's. */
  subject: string | null;
  /** Markdown; the sequence tokens are filled in when it is written. */
  body: string;
  /** Null sends it now. */
  sendAt: string | null;
  replyInThread: boolean;
}

export interface ScheduledMessage {
  id: string;
  status: MessageStatus;
  sendAt: string;
  subject: string | null;
  inThread: boolean;
}

export function listConversations(
  projectId: string,
  view: ConversationView,
  who: ConversationScope,
  query: string,
  signal?: AbortSignal,
): Promise<ConversationList> {
  const params = new URLSearchParams({ view, who });
  if (query.trim()) params.set("q", query.trim());
  return request<ConversationList>(`/projects/${projectId}/outreach/conversations?${params}`, { signal });
}

export function getConversation(projectId: string, candidateId: string, signal?: AbortSignal): Promise<Conversation> {
  return request<Conversation>(`/projects/${projectId}/outreach/conversations/${candidateId}`, { signal });
}

export function sendMessage(
  projectId: string,
  candidateId: string,
  message: SendMessageRequest,
): Promise<ScheduledMessage> {
  return request<ScheduledMessage>(`/projects/${projectId}/outreach/conversations/${candidateId}/messages`, {
    method: "POST",
    body: message,
  });
}

export function cancelMessage(projectId: string, messageId: string): Promise<void> {
  return request<void>(`/projects/${projectId}/outreach/messages/${messageId}`, { method: "DELETE" });
}
