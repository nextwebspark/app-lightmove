import type { SuggestedPerson } from "../api/sourcingApi";

const TITLE_LEVEL_LABELS: Record<string, string> = {
  TOP: "C-level",
  SENIOR_VICE_PRESIDENT: "SVP / EVP level",
  HEAD: "Head or Director level",
  MANAGER: "Manager level",
};

function split(reason: string): [string, string | null] {
  const at = reason.indexOf(":");
  return at < 0 ? [reason, null] : [reason.slice(0, at), reason.slice(at + 1)];
}

/**
 * A reason code as the chip under someone who fits reads it; null for one that shows as no chip. The countries
 * are named through {@code countryName}, which falls back to the code.
 */
export function reasonChip(reason: string, countryName: (code: string) => string): string | null {
  const [code, value] = split(reason);
  switch (code) {
    case "ROLE_WORD":
    case "SEARCH_WORD":
      return value ? `${value} in title` : null;
    case "TITLE_CONFIRMED":
      return "Title confirmed";
    case "LEVEL":
      return value ? (TITLE_LEVEL_LABELS[value] ?? null) : null;
    case "LIVES_IN_ROLE_COUNTRY":
    case "LIVES_IN_REGION":
    case "LIVES_ELSEWHERE":
      return value ? `Lives in ${countryName(value)}` : null;
    case "FOUND_BY_BOTH":
      return "Found by two sources";
    case "NEW_IN_ROLE":
      return value ? `New in role · ${value} mo` : "New in role";
    default:
      return null;
  }
}

/** Why someone who was found does not fit the role, in the panel's words. */
export function whyNot(person: SuggestedPerson): string {
  const [code, value] = split(person.reasons[0] ?? "");
  switch (code) {
    case "BELOW_LEVEL":
      return "Below the levels searched";
    case "NO_FUNCTION_WORD":
      return "None of the title words";
    case "LEAVE_OUT":
      return value ? `Leave-out word: ${value}` : "A leave-out word";
    case "NOT_THE_TOP_SEAT":
      return "Not the company's top role";
    default:
      return "Doesn't fit the role";
  }
}

/** The level a title reads at, from its LEVEL reason; null for someone with no title or who does not fit. */
export function titleLevelOf(person: SuggestedPerson): string | null {
  const level = person.reasons.map(split).find(([code]) => code === "LEVEL");
  return level?.[1] ?? null;
}
