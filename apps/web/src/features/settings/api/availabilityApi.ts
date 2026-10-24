import { request } from "../../../lib/apiClient";
import type { Weekday } from "../../outreach/api/sequenceApi";

/**
 * Working hours and the daily email cap: the workspace's default (an admin's to change) and each consultant's own,
 * where a null field is the workspace's. A booking link offers times inside the hours; a consultant booking a call
 * themselves is never held to them.
 */

export const WORKSPACE_AVAILABILITY_KEY = ["workspace-availability"] as const;
export const MY_AVAILABILITY_KEY = ["my-availability"] as const;

export interface WorkingDay {
  day: Weekday;
  /** `HH:mm`, or `HH:mm:ss` as the server writes it. */
  start: string;
  end: string;
}

export interface WorkspaceAvailability {
  weeklyHours: WorkingDay[];
  /** False while the week is the one the firm's country keeps. */
  weeklyHoursChosen: boolean;
  /** The country the week was read from while nobody has chosen one; null otherwise. */
  suggestedFromCountry: string | null;
  dailyEmailCap: number;
  dailyEmailCapChosen: boolean;
  maxDailyEmailCap: number;
}

export interface MyAvailability {
  /** Null: the workspace's. */
  weeklyHours: WorkingDay[] | null;
  workspaceWeeklyHours: WorkingDay[];
  /** Null: the workspace's. */
  dailyEmailCap: number | null;
  workspaceDailyEmailCap: number;
  timeZone: string;
  /** The connected calendar states its own working hours (Outlook), which come first. */
  calendarStatesHours: boolean;
}

export interface AvailabilityChange {
  weeklyHours: WorkingDay[] | null;
  dailyEmailCap: number | null;
}

export function getWorkspaceAvailability(signal?: AbortSignal): Promise<WorkspaceAvailability> {
  return request<WorkspaceAvailability>("/workspace/availability", { signal });
}

export function putWorkspaceAvailability(change: AvailabilityChange): Promise<WorkspaceAvailability> {
  return request<WorkspaceAvailability>("/workspace/availability", { method: "PUT", body: change });
}

export function getMyAvailability(signal?: AbortSignal): Promise<MyAvailability> {
  return request<MyAvailability>("/me/availability", { signal });
}

export function putMyAvailability(change: AvailabilityChange): Promise<MyAvailability> {
  return request<MyAvailability>("/me/availability", { method: "PUT", body: change });
}
