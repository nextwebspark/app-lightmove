import type { SectorGroup } from "../api/types";

/**
 * Which sectors sit beside which — the source for the panel's "Based on your selection" chips.
 *
 * <p>Advice with no counts that reaches no query: picking a suggestion expands to the same leaf
 * industries a direct pick would. It lives with the UI rather than the server for that reason, and
 * a name the server no longer groups renders no chip rather than one that selects nothing.
 */
const ADJACENT_SECTORS: Record<string, string[]> = {
  "Technology": ["Financial Services", "Telecommunications", "Education", "Media & Entertainment", "Professional Services"],
  "Healthcare": ["Education", "Government & Public Sector", "Nonprofit & Social"],
  "Financial Services": ["Technology", "Real Estate", "Professional Services"],
  "Real Estate": ["Financial Services", "Construction", "Hospitality & Tourism"],
  "Manufacturing": ["Energy & Utilities", "Construction", "Agriculture", "Aerospace & Defense", "Transportation", "Automotive"],
  "Retail & Consumer": ["Media & Entertainment", "Transportation", "Automotive", "Food & Beverage"],
  "Energy & Utilities": ["Manufacturing", "Construction", "Government & Public Sector"],
  "Construction": ["Real Estate", "Manufacturing", "Energy & Utilities"],
  "Telecommunications": ["Technology", "Media & Entertainment"],
  "Hospitality & Tourism": ["Real Estate", "Transportation", "Food & Beverage"],
  "Education": ["Technology", "Healthcare", "Government & Public Sector", "Nonprofit & Social"],
  "Agriculture": ["Manufacturing", "Food & Beverage"],
  "Media & Entertainment": ["Technology", "Retail & Consumer", "Telecommunications"],
  "Professional Services": ["Technology", "Financial Services", "Government & Public Sector"],
  "Aerospace & Defense": ["Manufacturing", "Transportation", "Government & Public Sector"],
  "Transportation": ["Manufacturing", "Retail & Consumer", "Hospitality & Tourism", "Aerospace & Defense", "Automotive"],
  "Automotive": ["Manufacturing", "Retail & Consumer", "Transportation"],
  "Food & Beverage": ["Retail & Consumer", "Hospitality & Tourism", "Agriculture"],
  "Government & Public Sector": ["Healthcare", "Energy & Utilities", "Education", "Professional Services", "Aerospace & Defense", "Nonprofit & Social"],
  "Nonprofit & Social": ["Healthcare", "Education", "Government & Public Sector"],
};

/** The sector's neighbours, keeping only those the server still groups. */
export function adjacentTo(group: SectorGroup, known: Map<string, SectorGroup>): string[] {
  return (ADJACENT_SECTORS[group.name] ?? []).filter((name) => known.has(name));
}

/** Used by the guard test. */
export function adjacencyEntries(): [string, string[]][] {
  return Object.entries(ADJACENT_SECTORS);
}
