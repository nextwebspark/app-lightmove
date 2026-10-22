/**
 * Asks the API to transcribe one captured piece with the notes' own transcriber (#978), the canary job's ID token as
 * the credential, and answers what it heard. Throws when it could not be heard: the API, or its model, unreachable.
 */
export async function transcribePiece(apiBaseUrl: string, idToken: string, piece: Uint8Array,
                                      fetcher: typeof fetch = fetch): Promise<string> {
  const response = await fetcher(`${apiBaseUrl}/api/v1/internal/notetaker/canary/hearing`, {
    method: "POST",
    headers: { "X-Notetaker-Bot-Identity": idToken, "Content-Type": "application/octet-stream" },
    body: piece as BodyInit,
    signal: AbortSignal.timeout(120_000),
  });
  if (!response.ok) {
    throw new Error(`the canary hearing answered ${response.status}`);
  }
  const answer = (await response.json()) as { heard?: string };
  return answer.heard ?? "";
}
