import { formatInstantDate, formatTimeAgo } from "../../../lib/format";
import type { OAuthGrant } from "../api/types";

/** "connected 12 Sep 2026 · last used 2 hours ago", naming whose connection it is on the All view. */
export function grantMetaOf(grant: OAuthGrant, showOwner: boolean, now: Date = new Date()): string {
  const parts: string[] = [];
  if (showOwner) parts.push(grant.ownerName);
  parts.push(`connected ${formatInstantDate(grant.connectedAt)}`);
  parts.push(grant.lastUsedAt ? `last used ${formatTimeAgo(grant.lastUsedAt, now)}` : "never used");
  return parts.join(" · ");
}

/** Where the app is known from: its document's host, else the address it returns to, as the consent screen showed. */
export function grantHostOf(grant: OAuthGrant): string {
  if (grant.clientHost) return grant.clientHost;
  const redirect = grant.redirectHost ?? "an unknown address";
  return grant.clientKind === "DCR" ? `registered itself · ${redirect}` : redirect;
}

export const MCP_GUIDE_PATH = "/docs/mcp";

/** The server's MCP endpoint on this deployment's own origin, which an AI app is given as a connector. */
export function mcpServerUrl(origin: string = window.location.origin): string {
  return `${origin}/api/v1/mcp`;
}
