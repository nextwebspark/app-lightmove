import { useQueryClient } from "@tanstack/react-query";
import { useCallback } from "react";
import { CANDIDATES_KEY_PREFIX } from "../features/candidates/api/candidatesApi";
import { STRATEGY_COMPANIES_KEY_PREFIX } from "../features/strategy/api/strategyApi";
import { TALENT_MAP_KEY_PREFIX } from "../features/talentmap/api/talentMapApi";
import { TRIAGE_KEY_PREFIX } from "../features/triage/api/triageApi";

/**
 * Everything that goes stale when companies are filed into a mandate. Lives above the features because the assistant
 * panel files against `proposal.projectId`, not necessarily the mandate on screen (#435); a registry would miss
 * features not yet imported. Prefix keys, so every page/sort/filter variant and the stage counts move together.
 */
export function useProjectRowsChanged(): (projectId: string) => Promise<void> {
  const queryClient = useQueryClient();

  return useCallback(
    async (projectId: string) => {
      const keys = [
        TRIAGE_KEY_PREFIX(projectId),
        CANDIDATES_KEY_PREFIX(projectId),
        TALENT_MAP_KEY_PREFIX(projectId),
        STRATEGY_COMPANIES_KEY_PREFIX(projectId),
      ];
      // Cancelled first, and awaited: a read still in flight would resolve after the invalidation
      // and reinstate the pre-write rows as fresh for the whole staleTime.
      await Promise.all(keys.map((queryKey) => queryClient.cancelQueries({ queryKey })));
      keys.forEach((queryKey) => void queryClient.invalidateQueries({ queryKey }));
    },
    [queryClient],
  );
}
