import { request, requestBlob } from "../../../lib/apiClient";
import type {
  CandidateTag,
  CandidateTagColour,
  MapToPositionResult,
  PersonNote,
  PersonRecord,
  PersonTimelinePage,
  PoolFilters,
  PoolPage,
  TimelineGroup,
  WritePersonNotePayload,
} from "./types";

/**
 * The workspace's people outside any one position — the Candidates page, its drawer and the tag
 * catalog. Staff-only: every route is the workspace's CANDIDATE_POOL_MANAGE, which no client holds.
 *
 * <p>Every key hangs off {@link POOL_KEY}, so a change to anyone refreshes the list, the drawer and the
 * feed in one invalidation.
 */
export const POOL_KEY = ["candidate-pool"] as const;
export const POOL_PAGE_KEY = (filters: PoolFilters, page: number, size: number) =>
  [...POOL_KEY, "page", filters, page, size] as const;
/** Outside {@link POOL_KEY}: a person's change never changes how many people there are. */
export const POOL_COUNT_KEY = ["candidate-pool-count"] as const;
export const PERSON_RECORD_KEY = (personId: string) => [...POOL_KEY, "person", personId] as const;
export const POOL_NOTES_KEY = (personId: string) => [...POOL_KEY, "notes", personId] as const;
export const POOL_TIMELINE_KEY = (personId: string, group: TimelineGroup | null) =>
  [...POOL_KEY, "timeline", personId, group] as const;
export const ACTIVITY_FEED_KEY = (filters: ActivityFilters) => [...POOL_KEY, "activity", filters] as const;
export const TAGS_KEY = ["candidate-tags"] as const;

/** The Activity view's filters; an empty value is no filter. */
export interface ActivityFilters {
  actor: string;
  position: string;
  group: TimelineGroup | "";
  /** An ISO instant, or empty for all time. */
  from: string;
}

function poolQueryOf(filters: PoolFilters): URLSearchParams {
  const params = new URLSearchParams();
  if (filters.q.trim()) params.set("q", filters.q.trim());
  if (filters.view !== "all") params.set("view", filters.view);
  for (const tagId of filters.tagIds) params.append("tag", tagId);
  if (filters.tagIds.length > 0) params.set("tagMatch", filters.tagMatch);
  if (filters.position) params.set("position", filters.position);
  if (filters.status) params.set("status", filters.status);
  if (filters.owner) params.set("owner", filters.owner);
  if (filters.country) params.set("country", filters.country);
  params.set("sort", filters.sort);
  params.set("direction", filters.direction);
  return params;
}

export function listPool(
  filters: PoolFilters,
  page: number,
  size: number,
  signal?: AbortSignal,
): Promise<PoolPage> {
  const params = poolQueryOf(filters);
  params.set("page", String(page));
  params.set("size", String(size));
  return request<PoolPage>(`/candidates?${params}`, { signal });
}

/** Everyone the filters show, as the CSV the server writes. */
export function exportPool(filters: PoolFilters): Promise<Blob> {
  return requestBlob(`/candidates/export?${poolQueryOf(filters)}`);
}

/** Exactly the people ticked — in the body, since a long selection would not fit in a query string. */
export function exportPeople(personIds: string[]): Promise<Blob> {
  return requestBlob("/candidates/export", { method: "POST", body: { personIds } });
}

export async function poolCount(signal?: AbortSignal): Promise<number> {
  return (await request<{ count: number }>("/candidates/count", { signal })).count;
}

/** The reads one person's change can alter: them, their notes and timeline, the list and the feed. */
export function personChangedKeys(personId: string) {
  return [
    PERSON_RECORD_KEY(personId),
    POOL_NOTES_KEY(personId),
    [...POOL_KEY, "timeline", personId],
    [...POOL_KEY, "page"],
    [...POOL_KEY, "activity"],
  ] as const;
}

export function getPerson(personId: string, signal?: AbortSignal): Promise<PersonRecord> {
  return request<PersonRecord>(`/candidates/${personId}`, { signal });
}

export function setOwner(personId: string, ownerUserId: string | null): Promise<PersonRecord> {
  return request<PersonRecord>(`/candidates/${personId}/owner`, { method: "PUT", body: { ownerUserId } });
}

export function setDoNotContact(
  personId: string,
  doNotContact: boolean,
  reason?: string,
): Promise<PersonRecord> {
  return request<PersonRecord>(`/candidates/${personId}/do-not-contact`, {
    method: "PUT",
    body: { doNotContact, reason },
  });
}

export function tagPerson(personId: string, tagId: string): Promise<PersonRecord> {
  return request<PersonRecord>(`/candidates/${personId}/tags/${tagId}`, { method: "PUT" });
}

export function untagPerson(personId: string, tagId: string): Promise<PersonRecord> {
  return request<PersonRecord>(`/candidates/${personId}/tags/${tagId}`, { method: "DELETE" });
}

export function retagPeople(personIds: string[], tagIds: string[], remove: boolean): Promise<{ changed: number }> {
  return request<{ changed: number }>("/candidates/bulk/tags", {
    method: "POST",
    body: { personIds, tagIds, remove },
  });
}

export function assignOwners(personIds: string[], ownerUserId: string | null): Promise<{ changed: number }> {
  return request<{ changed: number }>("/candidates/bulk/owner", {
    method: "POST",
    body: { personIds, ownerUserId },
  });
}

/** Adds the people to a position as Identified; someone already in it stays as they are. */
export function addToPosition(projectId: string, personIds: string[]): Promise<MapToPositionResult> {
  return request<MapToPositionResult>("/candidates/bulk/position", {
    method: "POST",
    body: { projectId, personIds },
  });
}

export function getPoolNotes(personId: string, signal?: AbortSignal): Promise<PersonNote[]> {
  return request<PersonNote[]>(`/candidates/${personId}/notes`, { signal });
}

export function writePoolNote(personId: string, note: WritePersonNotePayload): Promise<PersonNote> {
  return request<PersonNote>(`/candidates/${personId}/notes`, { method: "POST", body: note });
}

export function revisePoolNote(personId: string, noteId: string, note: WritePersonNotePayload): Promise<PersonNote> {
  return request<PersonNote>(`/candidates/${personId}/notes/${noteId}`, { method: "PUT", body: note });
}

export function removePoolNote(personId: string, noteId: string): Promise<void> {
  return request<void>(`/candidates/${personId}/notes/${noteId}`, { method: "DELETE" });
}

export function pinPoolNote(personId: string, noteId: string, pinned: boolean): Promise<PersonNote> {
  return request<PersonNote>(`/candidates/${personId}/notes/${noteId}/pin`, {
    method: "PATCH",
    body: { pinned },
  });
}

export function getPoolTimeline(
  personId: string,
  group: TimelineGroup | null,
  before: number | null,
  signal?: AbortSignal,
): Promise<PersonTimelinePage> {
  const params = new URLSearchParams();
  if (group) params.set("group", group);
  if (before != null) params.set("before", String(before));
  const query = params.size > 0 ? `?${params}` : "";
  return request<PersonTimelinePage>(`/candidates/${personId}/timeline${query}`, { signal });
}

export function getActivityFeed(
  filters: ActivityFilters,
  before: number | null,
  signal?: AbortSignal,
): Promise<PersonTimelinePage> {
  const params = new URLSearchParams();
  if (filters.actor) params.set("actor", filters.actor);
  if (filters.position) params.set("position", filters.position);
  if (filters.group) params.set("group", filters.group);
  if (filters.from) params.set("from", filters.from);
  if (before != null) params.set("before", String(before));
  params.set("limit", "24");
  return request<PersonTimelinePage>(`/candidates/activity?${params}`, { signal });
}

export function tagCatalog(signal?: AbortSignal): Promise<CandidateTag[]> {
  return request<CandidateTag[]>("/candidate-tags", { signal });
}

export function createTag(label: string, colour?: CandidateTagColour): Promise<CandidateTag> {
  return request<CandidateTag>("/candidate-tags", { method: "POST", body: { label, colour } });
}

/** An admin's change; each field left out is left as it is. */
export function updateTag(
  tagId: string,
  change: { label?: string; colour?: CandidateTagColour; retired?: boolean },
): Promise<CandidateTag> {
  return request<CandidateTag>(`/candidate-tags/${tagId}`, { method: "PATCH", body: change });
}
