import type { TeamRange } from "../api/reportApi";
import { addDays } from "./figures";

export type TeamRangeKey = "all" | "week" | "7d" | "30d" | "custom";

export const TEAM_RANGES: { key: TeamRangeKey; label: string }[] = [
  { key: "all", label: "All time" },
  { key: "week", label: "This week" },
  { key: "7d", label: "Last 7 days" },
  { key: "30d", label: "Last 30 days" },
  { key: "custom", label: "Custom" },
];

export const DEFAULT_TEAM_RANGE: TeamRangeKey = "30d";

/**
 * The dates a pill asks the server for, counted back from the report's own `asOf` (a UTC date)
 * rather than the browser's clock, so the table and the chapter above it agree on what "today" is.
 * "This week" starts on Sunday: the GCC working week, whose weekend is Friday–Saturday.
 */
export function teamRangeOf(key: TeamRangeKey, asOf: string, custom: TeamRange): TeamRange {
  switch (key) {
    case "all":
      return {};
    case "week":
      return { from: addDays(asOf, -new Date(`${asOf}T00:00:00Z`).getUTCDay()), to: asOf };
    case "7d":
      return { from: addDays(asOf, -6), to: asOf };
    case "30d":
      return { from: addDays(asOf, -29), to: asOf };
    case "custom":
      return custom;
  }
}
