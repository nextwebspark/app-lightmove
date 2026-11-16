import type { LookedUpProfile } from "../api/types";
import type { CandidateForm } from "./candidateForm";

/** The fields a lookup fills, as the form holds them. A field research left blank is absent, never cleared. */
export type LookupFill = Partial<
  Pick<
    CandidateForm,
    | "fullName"
    | "title"
    | "employerName"
    | "locationCity"
    | "locationCountry"
    | "summary"
    | "languages"
    | "career"
  >
>;

/** `employerLocked`: opened from a company's row, the employer is that company and research does not move it. */
export function lookupFillOf(profile: LookedUpProfile, employerLocked: boolean): LookupFill {
  const fill: LookupFill = {};
  const text = (value: string | null, max: number) => value?.trim().slice(0, max) || undefined;
  fill.fullName = text(profile.fullName, 200);
  fill.title = text(profile.title, 200);
  if (!employerLocked) fill.employerName = text(profile.employerName, 200);
  fill.locationCity = text(profile.locationCity, 100);
  fill.locationCountry = text(profile.locationCountry, 100);
  fill.summary = text(profile.summary, 4000);
  fill.languages = text(profile.languages.join(", "), 1200);
  if (profile.career.length > 0) {
    fill.career = profile.career.slice(0, 25).map((post) => ({
      company: post.company?.slice(0, 200) ?? "",
      title: post.title?.slice(0, 200) ?? "",
      period: post.period?.slice(0, 60) ?? "",
      location: post.location ?? null,
    }));
  }
  return Object.fromEntries(Object.entries(fill).filter(([, value]) => value !== undefined)) as LookupFill;
}

/** What a field holds once a lookup's value is taken back out of it. */
export function emptyValueOf(key: keyof LookupFill): string | [] {
  return key === "career" ? [] : "";
}

/** Nothing typed there yet: an empty string, or a career with no posts. */
export function isBlankValue(value: unknown): boolean {
  return value === "" || value === undefined || value === null || (Array.isArray(value) && value.length === 0);
}
