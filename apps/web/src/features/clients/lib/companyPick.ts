import type { CompanySuggestion } from "../../strategy/api/types";

/**
 * A company chosen for a new client: a row of the universe, or a record typed in because the market
 * does not carry it. Both entrances into client creation hold one of these before they post anything.
 */
export type CompanyPick =
  | { source: "universe"; company: CompanySuggestion }
  | { source: "custom"; name: string; domain: string };

/**
 * The name the pick will be filed under — the universe's canonical name for a DB pick, the typed one
 * otherwise. It is the name the server answers `CLIENT_ALREADY_EXISTS` about, which is what makes it
 * the right side of a duplicate comparison.
 */
export function pickedCompanyName(pick: CompanyPick): string {
  return pick.source === "universe" ? pick.company.companyName : pick.name;
}

/** A custom record has no mark; `CompanyLogo` renders its initial instead. */
export function pickedCompanyLogo(pick: CompanyPick): string | null {
  return pick.source === "universe" ? pick.company.logoUrl : null;
}

/** Where a company is, city first — the subtext under its name wherever a suggestion is rendered. */
export function companyLocation(company: CompanySuggestion): string {
  return [company.companyCity, company.companyCountry].filter(Boolean).join(", ");
}
