import type { LightMoveApiClient } from "./lightMoveApiClient";
import type { CapturedCandidate, HeldCandidate, TriagedCompany } from "./types";

/**
 * What a position already holds for the page the panel stands on, and the reads and writes its record makes.
 * A lookup answers null (the API's 204) when the position holds nothing there.
 */

export async function heldCandidate(
  api: LightMoveApiClient,
  projectId: string,
  linkedinUrl: string,
): Promise<HeldCandidate | null> {
  const query = new URLSearchParams({ linkedinUrl });
  return (await api.request<HeldCandidate | undefined>(`/projects/${projectId}/candidates/held?${query}`)) ?? null;
}

export async function heldCompany(
  api: LightMoveApiClient,
  projectId: string,
  linkedinUrl: string | null,
  name: string | null,
): Promise<TriagedCompany | null> {
  const query = new URLSearchParams();
  if (linkedinUrl) query.set("linkedinUrl", linkedinUrl);
  if (name) query.set("name", name);
  return (await api.request<TriagedCompany | undefined>(`/projects/${projectId}/triage/held?${query}`)) ?? null;
}

export function readCandidate(api: LightMoveApiClient, projectId: string, candidateId: string) {
  return api.request<CapturedCandidate>(`/projects/${projectId}/candidates/${candidateId}`);
}

/** Moves the executive to another of the workspace's statuses, by its key. */
export function changeCandidateStatus(
  api: LightMoveApiClient,
  projectId: string,
  candidateId: string,
  status: string,
): Promise<CapturedCandidate> {
  return api.request<CapturedCandidate>(`/projects/${projectId}/candidates/${candidateId}`, {
    method: "PATCH",
    body: { status },
  });
}

/**
 * The stored profile photo as a data URL, or null for someone with none. A data URL because the panel cannot send
 * the bearer token an `<img src>` would need, and the worker is the only context that holds it.
 */
export async function candidatePhoto(
  api: LightMoveApiClient,
  projectId: string,
  candidateId: string,
): Promise<string | null> {
  const photo = await api.requestBlob(`/projects/${projectId}/candidates/${candidateId}/photo`);
  return photo ? dataUrlOf(photo) : null;
}

async function dataUrlOf(blob: Blob): Promise<string> {
  const bytes = new Uint8Array(await blob.arrayBuffer());
  let binary = "";
  const chunk = 0x8000;
  for (let offset = 0; offset < bytes.length; offset += chunk) {
    binary += String.fromCharCode(...bytes.subarray(offset, offset + chunk));
  }
  return `data:${blob.type || "image/jpeg"};base64,${btoa(binary)}`;
}
