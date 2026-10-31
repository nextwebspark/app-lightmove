import { request, requestBlob } from "../../../lib/apiClient";
import { saveBlob } from "../../../lib/saveBlob";
import type { AuditLogGroup, AuditLogPage, AuditLogPerson } from "./types";

/** Settings → Security → Audit log, WORKSPACE_MANAGE's alone. */

export const AUDIT_LOG_KEY = ["workspace", "audit-log"] as const;
export const AUDIT_LOG_PAGE_KEY = (filters: AuditLogFilters) => [...AUDIT_LOG_KEY, "page", filters] as const;
export const AUDIT_LOG_PEOPLE_KEY = [...AUDIT_LOG_KEY, "people"] as const;

const PAGE_SIZE = 50;

/** The filter row; an empty value is no filter. */
export interface AuditLogFilters {
  actor: string;
  group: AuditLogGroup | "";
  outcome: "SUCCESS" | "FAILURE" | "";
  /** ISO yyyy-MM-dd, the first day shown, in the viewer's own time zone. */
  fromDay: string;
  /** ISO yyyy-MM-dd, the last day shown, in the viewer's own time zone. */
  toDay: string;
}

export const NO_AUDIT_LOG_FILTERS: AuditLogFilters = { actor: "", group: "", outcome: "", fromDay: "", toDay: "" };

export function auditLog(filters: AuditLogFilters, before: number | null, signal?: AbortSignal): Promise<AuditLogPage> {
  const params = queryOf(filters);
  if (before != null) params.set("before", String(before));
  params.set("limit", String(PAGE_SIZE));
  return request<AuditLogPage>(`/workspace/audit-events?${params}`, { signal });
}

export function auditLogPeople(signal?: AbortSignal): Promise<AuditLogPerson[]> {
  return request<AuditLogPerson[]>("/workspace/audit-events/people", { signal });
}

/** The same filtered view as a CSV, which the server records in the log itself. */
export async function saveAuditLogCsv(filters: AuditLogFilters): Promise<void> {
  const query = queryOf(filters).toString();
  const blob = await requestBlob(`/workspace/audit-events/export${query ? `?${query}` : ""}`);
  saveBlob(blob, `uncava-audit-log-${new Date().toISOString().slice(0, 10)}.csv`);
}

function queryOf(filters: AuditLogFilters): URLSearchParams {
  const params = new URLSearchParams();
  if (filters.actor) params.set("actor", filters.actor);
  if (filters.group) params.set("group", filters.group);
  if (filters.outcome) params.set("outcome", filters.outcome);
  if (filters.fromDay) params.set("from", startOfDay(filters.fromDay).toISOString());
  if (filters.toDay) {
    // The server's `to` is exclusive, so the last day shown ends where the next one starts.
    const dayAfter = startOfDay(filters.toDay);
    dayAfter.setDate(dayAfter.getDate() + 1);
    params.set("to", dayAfter.toISOString());
  }
  return params;
}

function startOfDay(isoDate: string): Date {
  const [year, month, day] = isoDate.split("-").map(Number);
  return new Date(year, month - 1, day);
}
