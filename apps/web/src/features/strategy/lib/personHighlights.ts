import type { CandidateCareerEntry } from "../../candidates/api/types";
import { isCurrent, parsePeriod, tenureOf } from "../../candidates/lib/careerTimeline";
import type { PersonResult } from "../api/types";

/**
 * What a People card says at a glance, read from the profile a search already bought. Built on the
 * drawer's own period reading, so a card and the Experience fold never disagree about a tenure.
 */

export interface CurrentTenure {
  since: number;
  /** "5 yrs 2 mos", or null under a month. */
  length: string | null;
}

/** The seat they hold now — the first current post — and how long they have held it. */
export function currentTenure(career: readonly CandidateCareerEntry[], now: Date = new Date()): CurrentTenure | null {
  const post = career.find((entry) => isCurrent(entry.period));
  const parsed = parsePeriod(post?.period);
  if (!parsed) return null;
  return { since: parsed.start.year, length: tenureOf(parsed, now) };
}

/** Whole years since the earliest start anywhere on the career; null when no period reads. */
export function totalYears(career: readonly CandidateCareerEntry[], now: Date = new Date()): number | null {
  const starts = career
    .map((entry) => parsePeriod(entry.period)?.start.year)
    .filter((year): year is number => year !== undefined);
  if (starts.length === 0) return null;
  const years = now.getFullYear() - Math.min(...starts);
  return years > 0 ? years : null;
}

/** The posts before the one held now, newest first as stored — the "where from" a researcher reads. */
export function previousRoles(career: readonly CandidateCareerEntry[], limit: number): CandidateCareerEntry[] {
  return career.filter((entry) => !isCurrent(entry.period) && (entry.title || entry.company)).slice(0, limit);
}

export interface PersonSignals {
  chips: string[];
  overflow: number;
}

/** A Gulf mandate asks after Arabic first; any other language follows in the profile's order. */
const PREFERRED_LANGUAGE = /^arabic\b/i;

/**
 * The small facts worth a chip: where they studied, a language, credentials. Capped, with the rest
 * counted, so every card keeps one height of chip row.
 */
export function signalsOf(person: PersonResult, limit = 3): PersonSignals {
  const all: string[] = [];
  const school = person.education.find((entry) => entry.school)?.school;
  if (school) all.push(school);
  const languages = [...person.languages].sort(
    (a, b) => Number(PREFERRED_LANGUAGE.test(b)) - Number(PREFERRED_LANGUAGE.test(a)),
  );
  all.push(...languages);
  const certifications = person.details?.certifications.length ?? 0;
  if (certifications > 0) all.push(`${certifications} ${certifications === 1 ? "certification" : "certifications"}`);
  return { chips: all.slice(0, limit), overflow: Math.max(0, all.length - limit) };
}
