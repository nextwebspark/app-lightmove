import type { ApiKeyScope } from "../../settings/api/types";
import type { ConsentContext } from "../api/types";

/** What each scope reads, in the API's order; the last two are personal data and say so. */
export const CONSENT_SCOPES: readonly { scope: ApiKeyScope; label: string; note: string; personalData: boolean }[] = [
  { scope: "projects:read", label: "Positions", note: "Title, stage, type, dates and counts.", personalData: false },
  {
    scope: "companies:read",
    label: "Companies",
    note: "Each position's companies: in universe, shortlisted and declined.",
    personalData: false,
  },
  {
    scope: "candidates:read",
    label: "Executives",
    note: "Each position's executives: profile, company and status.",
    personalData: false,
  },
  {
    scope: "candidates.contacts:read",
    label: "Contact details",
    note: "Their emails and phone numbers.",
    personalData: true,
  },
  {
    scope: "candidates.compensation:read",
    label: "Compensation",
    note: "Their salary, bonus, allowances and incentives.",
    personalData: true,
  },
];

/** The AI client's request as it arrived, or the error the authorization server sent the browser here with. */
export type ClientRequest =
  | { kind: "request"; params: URLSearchParams; clientId: string; redirectUri: string | null; scope: string | null }
  | { kind: "error"; code: string };

export function readClientRequest(search: string): ClientRequest {
  const params = new URLSearchParams(search);
  const error = params.get("error");
  if (error) return { kind: "error", code: error };
  const clientId = params.get("client_id");
  if (!clientId) return { kind: "error", code: "invalid_request" };
  // Ours to set from the picker, never the caller's.
  params.delete("workspace_id");
  return { kind: "request", params, clientId, redirectUri: params.get("redirect_uri"), scope: params.get("scope") };
}

/** What the screen offers, ticked: everything asked for but personal data, which the person opts into. */
export function initialScopes(requested: readonly ApiKeyScope[]): ApiKeyScope[] {
  return CONSENT_SCOPES.filter(({ scope, personalData }) => !personalData && requested.includes(scope)).map(
    ({ scope }) => scope,
  );
}

/** How a redirect back to the client reads: a code, the person's own no, or a refusal by the server. */
export function outcomeOf(redirectUri: string): "granted" | "denied" | "failed" {
  const params = new URL(redirectUri).searchParams;
  if (params.has("code")) return "granted";
  return params.get("error") === "access_denied" ? "denied" : "failed";
}

/** "positions, companies and executives". */
export function grantedSummary(scopes: readonly ApiKeyScope[]): string {
  const labels = CONSENT_SCOPES.filter(({ scope }) => scopes.includes(scope)).map(({ label }) => label.toLowerCase());
  if (labels.length === 0) return "nothing";
  if (labels.length === 1) return labels[0];
  return `${labels.slice(0, -1).join(", ")} and ${labels[labels.length - 1]}`;
}

/** The one line under the app's name that says how Uncava knows it. */
export function clientSubtitle(context: ConsentContext): string {
  if (context.clientHost) return context.clientHost;
  if (context.clientKind === "DCR") return "Registered itself";
  return hostOf(context.clientUri) ?? context.redirectHost;
}

/** The apps whose own mark we draw, by the host their metadata document is served from. */
const KNOWN_CLIENT_HOSTS = { "claude.ai": "claude", "chatgpt.com": "chatgpt" } as const;

export type KnownClientMark = (typeof KNOWN_CLIENT_HOSTS)[keyof typeof KNOWN_CLIENT_HOSTS];

/**
 * Claude's or ChatGPT's own mark, only for a metadata document the server verified on that host — a URL prefix we list,
 * so the host is proven, not typed. A self-registered app naming itself "Claude" gets nothing here.
 */
export function knownClientMarkOf(context: ConsentContext): KnownClientMark | null {
  if (!context.verified || context.clientKind !== "CIMD" || !context.clientHost) return null;
  return KNOWN_CLIENT_HOSTS[context.clientHost as keyof typeof KNOWN_CLIENT_HOSTS] ?? null;
}

function hostOf(uri: string | null): string | null {
  if (!uri) return null;
  try {
    return new URL(uri).host;
  } catch {
    return null;
  }
}

/** Keyed on the authorization server's OAuth error or our own problem code; never on a description. */
export function refusalMessage(code: string): string {
  switch (code) {
    case "OAUTH_CLIENT_NOT_FOUND":
    case "invalid_client":
    case "unauthorized_client":
      return "The app isn't one Uncava knows, or asked to send you back to an address it never registered.";
    case "OAUTH_REQUEST_NOT_FOUND":
      return "This request has expired or was already answered.";
    case "invalid_scope":
      return "The app asked for access Uncava doesn't offer.";
    case "server_error":
    case "temporarily_unavailable":
    case "INTERNAL_ERROR":
      return "Uncava couldn't complete the connection. Try again in a moment.";
    default:
      return "The app's request was incomplete or not one Uncava accepts.";
  }
}
