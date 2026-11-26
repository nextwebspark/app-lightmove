import { formatDate, formatInstantDate } from "../../../../lib/datetime/format";
import type { SubprocessorNotice } from "../api/types";

export const SUBPROCESSORS_PATH = "/platform/subprocessors";

export const noticePath = (noticeId: string) => `${SUBPROCESSORS_PATH}/${encodeURIComponent(noticeId)}`;

/** The contract's notice period: a change takes effect no sooner than this many days after it is published. */
export const NOTICE_PERIOD_DAYS = 30;

export const NOTICE_LIMITS = { provider: 120, region: 80, receives: 200, purpose: 200, listUrl: 500 } as const;

export const EFFECTIVE_ON_TOO_SOON = "Takes effect must be at least 30 days from today.";
export const LINK_NOT_HTTPS = "Enter a link that starts with https://";

const HTTPS_LINK = /^https:\/\/\S+$/;

export function isHttpsLink(link: string): boolean {
  return HTTPS_LINK.test(link.trim());
}

/** The earliest day a change may take effect, as the server counts it: today in UTC plus the notice period. */
export function earliestEffectiveOn(now: Date = new Date()): string {
  const earliest = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() + NOTICE_PERIOD_DAYS));
  return earliest.toISOString().slice(0, 10);
}

export function admins(count: number): string {
  return count === 1 ? "1 admin" : `${count} admins`;
}

export function workspaceAdmins(count: number): string {
  return count === 1 ? "1 workspace admin" : `${count} workspace admins`;
}

/** Under the emailed count: still retried until the change takes effect, given up after. */
export function failedLabel(notice: SubprocessorNotice): string {
  return notice.inEffect ? `${notice.failed} never delivered` : `${notice.failed} failed, retrying`;
}

export function objectionsLabel(count: number): string {
  return count === 1 ? "1 objection" : `${count} objections`;
}

export function effectiveLabel(notice: SubprocessorNotice): string {
  return formatDate(notice.effectiveOn);
}

export function publishedLabel(notice: SubprocessorNotice): string {
  const day = formatInstantDate(notice.publishedAt) ?? "—";
  return `${day} · ${notice.publishedBy ?? "Former member"}`;
}
