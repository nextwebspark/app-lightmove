import type { ProjectActivityEntry } from "../api/types";

/** One line of the side panel's Recent activity: a run of the same act by the same person, merged. */
export interface ActivityLine {
  key: string;
  actorName: string;
  actorUserId: string | null;
  actorAvatarUrl: string | null;
  text: string;
  occurredAt: string;
}

interface Phrase {
  /** Entries sharing a group key and an actor merge into one line. */
  group: string;
  count: number;
  /** The act for one of them, and for several. */
  one: string;
  many: (count: number) => string;
}

/**
 * The feed as sentences. Consecutive entries by one person doing one thing collapse — forty
 * companies declined in a sitting read as one line, and a brief's per-section autosaves as one edit.
 * An entry type the panel has no words for is dropped rather than printed as its code.
 */
export function activityLines(entries: ProjectActivityEntry[]): ActivityLine[] {
  const lines: (ActivityLine & { phrase: Phrase })[] = [];
  for (const entry of entries) {
    const phrase = phraseOf(entry);
    if (!phrase) continue;
    const previous = lines.at(-1);
    if (previous && previous.actorUserId === entry.actorUserId && previous.phrase.group === phrase.group) {
      const merged = { ...previous.phrase, count: previous.phrase.count + phrase.count };
      previous.phrase = merged;
      previous.text = textOf(merged);
      continue;
    }
    lines.push({
      key: String(entry.id),
      actorName: entry.actorName?.trim() || "Someone",
      actorUserId: entry.actorUserId,
      actorAvatarUrl: entry.actorAvatarUrl,
      text: textOf(phrase),
      occurredAt: entry.occurredAt,
      phrase,
    });
  }
  return lines.map(({ phrase: _phrase, ...line }) => line);
}

function textOf(phrase: Phrase): string {
  return phrase.count === 1 ? phrase.one : phrase.many(phrase.count);
}

const plural = (count: number, one: string, many: string) => `${count} ${count === 1 ? one : many}`;

const companies = (verb: string) => (count: number) => `${verb} ${plural(count, "company", "companies")}`;
const executives = (verb: string) => (count: number) => `${verb} ${plural(count, "executive", "executives")}`;

/** Triage stages arrive both as wire tokens (`inUniverse`) and as enum names (`IN_UNIVERSE`). */
function triageStage(status: string | undefined): "universe" | "shortlisted" | "declined" | null {
  const folded = status?.toLowerCase().replace(/_/g, "");
  if (folded === "inuniverse") return "universe";
  if (folded === "shortlisted" || folded === "declined") return folded;
  return null;
}

const TRIAGE_VERB = { universe: "added", shortlisted: "shortlisted", declined: "declined" } as const;

const CANDIDATE_STATUS: Record<string, string> = {
  identified: "Identified",
  contacted: "Contacted",
  engaged: "Engaged",
  interested: "Interested",
  notInterested: "Not interested",
  offLimits: "Off limits",
  outOfScope: "Out of scope",
};

const EXPORT_STAGE: Record<string, string> = {
  inUniverse: "universe",
  shortlisted: "shortlist",
  declined: "declined list",
};

function phraseOf(entry: ProjectActivityEntry): Phrase | null {
  const { details } = entry;
  const fixed = (group: string, one: string, many = one): Phrase => ({ group, count: 1, one, many: () => many });

  switch (entry.type) {
    case "PROJECT_CREATED":
      return fixed(entry.type, "created the position");
    case "POSITION_UPDATED":
      return fixed(entry.type, "updated the brief");
    case "POSITION_TEMPLATE_APPLIED":
      return fixed(entry.type, "drafted the brief from a role template");
    case "POSITION_PUBLISHED":
      return fixed(entry.type, "published the brief");
    case "POSITION_DOCUMENT_ATTACHED":
      return fixed(entry.type, "attached the position description");
    case "STRATEGY_UPDATED":
      return fixed(entry.type, "refined the market filter");
    case "STRATEGY_SEARCH_SAVED":
      return { group: entry.type, count: 1, one: "saved a market search", many: (n) => `saved ${n} market searches` };
    case "TRIAGE_COMPANY_ADDED":
    case "TRIAGE_COMPANY_CAPTURED":
      return { group: "COMPANY:universe", count: 1, one: "added a company", many: companies("added") };
    case "TRIAGE_BULK_ADDED": {
      const stage = triageStage(details.status) ?? "universe";
      const added = Number(details.added) || 0;
      if (added === 0) return null;
      const verb = TRIAGE_VERB[stage];
      return { group: `COMPANY:${stage}`, count: added, one: `${verb} a company`, many: companies(verb) };
    }
    case "TRIAGE_COMPANY_MOVED": {
      const stage = triageStage(details.status);
      if (!stage) return null;
      if (stage === "universe") {
        return {
          group: "COMPANY:restored",
          count: 1,
          one: "moved a company back to the universe",
          many: (n) => `moved ${plural(n, "company", "companies")} back to the universe`,
        };
      }
      const verb = TRIAGE_VERB[stage];
      return { group: `COMPANY:${stage}`, count: 1, one: `${verb} a company`, many: companies(verb) };
    }
    case "TRIAGE_COMPANY_REMOVED":
      return {
        group: entry.type,
        count: 1,
        one: details.companyName ? `removed ${details.companyName}` : "removed a company",
        many: companies("removed"),
      };
    case "CANDIDATE_ADDED":
      return { group: entry.type, count: 1, one: "mapped an executive", many: executives("mapped") };
    case "CANDIDATE_UPDATED": {
      const label = details.status ? CANDIDATE_STATUS[details.status] : undefined;
      if (!label) return null;
      return {
        group: `${entry.type}:${details.status}`,
        count: 1,
        one: `marked an executive ${label}`,
        many: (n) => `marked ${plural(n, "executive", "executives")} ${label}`,
      };
    }
    case "CANDIDATE_REMOVED":
      return {
        group: entry.type,
        count: 1,
        one: details.fullName ? `removed ${details.fullName}` : "removed an executive",
        many: executives("removed"),
      };
    case "SPREADSHEET_IMPORTED": {
      const imported = [
        details.companiesCreated && plural(Number(details.companiesCreated), "company", "companies"),
        details.candidatesCreated && plural(Number(details.candidatesCreated), "executive", "executives"),
      ].filter(Boolean);
      const file = details.fileName ? `imported ${details.fileName}` : "imported a spreadsheet";
      return fixed(`${entry.type}:${entry.id}`, imported.length ? `${file} — ${imported.join(", ")}` : file);
    }
    case "COMPANIES_EXPORTED": {
      const stage = details.stage ? EXPORT_STAGE[details.stage] : undefined;
      return fixed(`${entry.type}:${details.stage ?? ""}`, stage ? `exported the ${stage}` : "exported companies");
    }
    default:
      return null;
  }
}

/** "just now" · "12 minutes ago" · "3 hours ago" · "Yesterday" · "4 days ago" · then the date. */
export function formatActivityTime(isoInstant: string, now: Date = new Date()): string {
  const at = new Date(isoInstant);
  const elapsedMs = now.getTime() - at.getTime();
  if (Number.isNaN(elapsedMs)) return "—";

  const minutes = Math.floor(elapsedMs / 60_000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return minutes === 1 ? "1 minute ago" : `${minutes} minutes ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return hours === 1 ? "1 hour ago" : `${hours} hours ago`;
  const days = Math.floor(hours / 24);
  if (days === 1) return "Yesterday";
  if (days < 7) return `${days} days ago`;
  return at.toLocaleDateString("en-GB", { day: "2-digit", month: "short", year: "numeric" });
}
