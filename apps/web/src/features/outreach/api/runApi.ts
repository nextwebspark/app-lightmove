import { request } from "../../../lib/apiClient";
import type { CandidateStatus } from "../../candidates/api/types";

/**
 * People in outreach on a position — the Outreach page's counts and table, one executive's run for
 * their drawer, and Stop. The position's `WORK_EXECUTE`, like every outreach route.
 */

export const OUTREACH_PEOPLE_KEY = (projectId: string) => ["outreach", projectId, "people"] as const;
export const CANDIDATE_OUTREACH_KEY = (projectId: string, candidateId: string) =>
  ["outreach", projectId, "candidate", candidateId] as const;

/** BOOKED: a call was booked with them, which ends the run as a reply does. */
export type RunStatus = "SCHEDULED" | "ACTIVE" | "REPLIED" | "BOUNCED" | "STOPPED" | "COMPLETED" | "BOOKED";

export type StopReason =
  | "MANUAL"
  | "DO_NOT_CONTACT"
  | "LEFT_THE_RUNNING"
  | "UNMAPPED"
  | "ADDRESS_REMOVED"
  | "MAILBOX_INACTIVE"
  | "SEND_FAILED"
  | "SEND_UNCERTAIN";

export interface OutreachRun {
  id: string;
  /** Null once the person is off the position; the run stays as the record of the approach. */
  candidateId: string | null;
  personId: string;
  fullName: string | null;
  title: string | null;
  companyName: string | null;
  candidateStatus: CandidateStatus | null;
  sequenceId: string;
  sequenceName: string | null;
  stepCount: number;
  sentCount: number;
  nextSendAt: string | null;
  lastSentAt: string | null;
  status: RunStatus;
  stopReason: StopReason | null;
  /** When it was answered, bounced, stopped or finished. */
  endedAt: string | null;
  senderUserId: string;
  senderName: string | null;
  /** The executive booked through the sender's link, rather than a consultant booking for them. */
  bookedViaLink: boolean;
}

export interface OutreachCounts {
  enrolled: number;
  emailsSent: number;
  reached: number;
  replied: number;
  inFlight: number;
  bounced: number;
  stopped: number;
  booked: number;
}

export interface OutreachOverview {
  counts: OutreachCounts;
  nextSendAt: string | null;
  people: OutreachRun[];
}

export type StepState = "SENT" | "SCHEDULED" | "WAITING" | "NOT_SENT";

export interface OutreachStep {
  number: number;
  /** The first step's only; the others reply in its thread. */
  subject: string | null;
  state: StepState;
  at: string | null;
  /** Why a step will never go: REPLIED, BOUNCED, BOOKED, or a stop reason. */
  notSentBecause: "REPLIED" | "BOUNCED" | "BOOKED" | StopReason | null;
}

export interface CandidateOutreach {
  run: OutreachRun | null;
  steps: OutreachStep[];
}

export function getOutreachPeople(projectId: string, signal?: AbortSignal): Promise<OutreachOverview> {
  return request<OutreachOverview>(`/projects/${projectId}/outreach/people`, { signal });
}

export function getCandidateOutreach(
  projectId: string,
  candidateId: string,
  signal?: AbortSignal,
): Promise<CandidateOutreach> {
  return request<CandidateOutreach>(`/projects/${projectId}/outreach/candidates/${candidateId}`, { signal });
}

export function stopRun(projectId: string, runId: string): Promise<void> {
  return request<void>(`/projects/${projectId}/outreach/enrollments/${runId}/stop`, { method: "POST" });
}
