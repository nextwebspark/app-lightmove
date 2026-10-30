import { useQuery } from "@tanstack/react-query";
import { ICONS } from "../../../components/layout/Icon";
import * as talentMapApi from "../../talentmap/api/talentMapApi";
import type { CompaniesView } from "../../talentmap/lib/useTalentMapPreferences";
import type { TriageCompanyStatus } from "../api/types";

/** How each view is named and drawn, wherever it is offered: the rail and the page's own switch. */
export const COMPANIES_VIEW_LABELS: Record<CompaniesView, { label: string; icon: string }> = {
  table: { label: "Table", icon: ICONS.table },
  map: { label: "Map", icon: ICONS.mapPin },
  pipeline: { label: "Pipeline", icon: ICONS.pipeline },
};

/**
 * The readings a Companies stage offers — In universe's table, map and pipeline, every other stage its table — shared
 * by the page and the rail, so the rail never offers a view the page would refuse.
 */
export function useCompaniesViews(stage: TriageCompanyStatus, canWrite: boolean) {
  // A refused or failed read means no map rather than a broken globe, and a token does not change while a tab is open.
  const mapConfig = useQuery({
    queryKey: talentMapApi.TALENT_MAP_CONFIG_KEY,
    queryFn: ({ signal }) => talentMapApi.getTalentMapConfig(signal),
    staleTime: Infinity,
  });
  if (stage !== "inUniverse") return { views: ["table"] as CompaniesView[], mapConfig };
  const mapOffered = mapConfig.data?.enabled === true && !!mapConfig.data.publicToken;
  // The pipeline is the position's WORK_EXECUTE, so a client seat reads the table and the map alone.
  const views: CompaniesView[] = [
    "table",
    ...(mapOffered ? (["map"] as const) : []),
    ...(canWrite ? (["pipeline"] as const) : []),
  ];
  return { views, mapConfig };
}

/**
 * What a link to a view carries when someone chose it — the rail, the page's own switch — so the view is remembered
 * for the position; a link that only names one, a notification's opening the pipeline, is a visit.
 */
export const CHOSEN_VIEW_STATE = { chosenView: true } as const;

export function isChosenView(state: unknown): boolean {
  return typeof state === "object" && state !== null && (state as { chosenView?: unknown }).chosenView === true;
}

/** A link names its view (a notification opens the pipeline); otherwise the one last chosen on this position. */
export function resolveCompaniesView(
  views: readonly CompaniesView[],
  requested: string | null,
  remembered: CompaniesView,
): CompaniesView {
  return views.find((offered) => offered === requested) ?? (views.includes(remembered) ? remembered : "table");
}
