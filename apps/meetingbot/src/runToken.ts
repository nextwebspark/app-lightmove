import type { BotConfig } from "./config.js";

/** The metadata server's own address on Cloud Run; it answers only from inside the job's container. */
export const METADATA_IDENTITY =
  "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/identity";

/**
 * The run's token: the one the API started the bot with, or — on Cloud Run, where the execution's environment is
 * readable by anyone who may view it — one taken from the API once, in exchange for an ID token of the job's own
 * service account, minted for the API alone. The token is never logged.
 */
export async function runTokenOf(config: BotConfig, fetcher: typeof fetch = fetch): Promise<string> {
  if (config.runToken) {
    return config.runToken;
  }
  const idToken = await identityToken(config.tokenAudience ?? "", fetcher);
  const exchanged = await fetcher(`${config.apiBaseUrl}/api/v1/internal/notetaker/runs/${config.runId}/token`, {
    method: "POST",
    headers: { "X-Notetaker-Bot-Identity": idToken },
    signal: AbortSignal.timeout(30_000),
  });
  if (!exchanged.ok) {
    throw new Error(`the run token exchange answered ${exchanged.status}`);
  }
  const answer = (await exchanged.json()) as { runToken?: string };
  if (!answer.runToken) {
    throw new Error("the run token exchange answered no token");
  }
  return answer.runToken;
}

/** An ID token of the job's own service account, minted for `audience` alone, from the metadata server. */
export async function identityToken(audience: string, fetcher: typeof fetch = fetch): Promise<string> {
  const identity = await fetcher(`${METADATA_IDENTITY}?audience=${encodeURIComponent(audience)}&format=full`,
    { headers: { "Metadata-Flavor": "Google" }, signal: AbortSignal.timeout(10_000) });
  if (!identity.ok) {
    throw new Error(`the metadata server answered ${identity.status} for an ID token`);
  }
  return (await identity.text()).trim();
}
