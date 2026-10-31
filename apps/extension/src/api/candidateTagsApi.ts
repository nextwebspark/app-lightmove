import type { LightMoveApiClient } from "./lightMoveApiClient";
import type { CandidateTag, PersonTags } from "./types";

/** The workspace's tag catalog, and the tags a person holds — the person's own, on every position. */

export function listCandidateTags(api: LightMoveApiClient): Promise<CandidateTag[]> {
  return api.request<CandidateTag[]>("/candidate-tags");
}

export function createCandidateTag(api: LightMoveApiClient, label: string): Promise<CandidateTag> {
  return api.request<CandidateTag>("/candidate-tags", { method: "POST", body: { label } });
}

export async function readPersonTags(api: LightMoveApiClient, personId: string): Promise<PersonTags> {
  const person = await api.request<PersonTags & { id?: string }>(`/candidates/${personId}`);
  return { personId, tagIds: person.tagIds ?? [] };
}

export async function tagPerson(api: LightMoveApiClient, personId: string, tagId: string): Promise<PersonTags> {
  const person = await api.request<PersonTags>(`/candidates/${personId}/tags/${tagId}`, { method: "PUT" });
  return { personId, tagIds: person.tagIds ?? [] };
}

export async function untagPerson(api: LightMoveApiClient, personId: string, tagId: string): Promise<PersonTags> {
  const person = await api.request<PersonTags>(`/candidates/${personId}/tags/${tagId}`, { method: "DELETE" });
  return { personId, tagIds: person.tagIds ?? [] };
}
