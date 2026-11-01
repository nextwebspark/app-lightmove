import { daysUntil } from "../../../lib/format";
import type { WorkspaceIntegration } from "../api/types";

/** The same 30 days the admins' warning emails start at. */
export const EXPIRY_WARNING_DAYS = 30;

export type IntegrationTone = "neutral" | "muted" | "ok" | "warning" | "failing";

export interface IntegrationStatus {
  pill: string;
  tone: IntegrationTone;
  summary: string;
}

/**
 * A provider card's pill and the line under its title. A refusal outranks an expiry, and an expiry outranks the
 * verified mark; an own app nobody has checked yet is plainly "Own app".
 */
export function integrationStatus(
  integration: WorkspaceIntegration,
  { openedOwn = false }: { openedOwn?: boolean } = {},
): IntegrationStatus {
  if (integration.mode === "SHARED") {
    if (openedOwn) return { pill: "Not saved yet", tone: "neutral", summary: "Your own app, not saved yet" };
    return integration.sharedOffered
      ? { pill: "Shared app", tone: "neutral", summary: "Uncava's shared app" }
      : {
          pill: "Not set up",
          tone: "muted",
          summary: "Not connected yet. Uncava's shared app isn't available here, so use your own.",
        };
  }

  const summary = ownAppSummary(integration);
  const daysLeft = secretDaysLeft(integration);
  if (integration.keysRefusedWith) return { pill: "Keys refused", tone: "failing", summary };
  if (daysLeft !== null && daysLeft < 0) return { pill: "Secret expired", tone: "failing", summary };
  if (daysLeft !== null && daysLeft <= EXPIRY_WARNING_DAYS) {
    return { pill: daysLeft === 0 ? "Expires today" : `Expires in ${dayCount(daysLeft)}`, tone: "warning", summary };
  }
  return integration.keysCheckedAt
    ? { pill: "Own app · Verified", tone: "ok", summary }
    : { pill: "Own app", tone: "neutral", summary };
}

/** Whole days until an own app's secret expires, negative once it has; null where it never does. */
export function secretDaysLeft(integration: WorkspaceIntegration): number | null {
  return integration.mode === "OWN" && integration.secretExpiresOn ? daysUntil(integration.secretExpiresOn) : null;
}

/** "3f2a9c71…8f05": a long client ID's start and end, enough to tell two apps apart. */
export function shortClientId(clientId: string): string {
  return clientId.length > 16 ? `${clientId.slice(0, 8)}…${clientId.slice(-4)}` : clientId;
}

export function dayCount(days: number): string {
  return days === 1 ? "1 day" : `${days} days`;
}

function ownAppSummary(integration: WorkspaceIntegration): string {
  return [
    "Your own app",
    integration.clientId ? shortClientId(integration.clientId) : null,
    integration.secretHint ? `secret ••••${integration.secretHint}` : null,
  ]
    .filter((part): part is string => part !== null)
    .join(" · ");
}
