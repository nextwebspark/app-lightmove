import { useQueryClient } from "@tanstack/react-query";
import { useCallback } from "react";
import { CANDIDATES_KEY_PREFIX } from "../features/candidates/api/candidatesApi";
import { STRATEGY_COMPANIES_KEY_PREFIX } from "../features/strategy/api/strategyApi";
import { TALENT_MAP_KEY_PREFIX } from "../features/talentmap/api/talentMapApi";
import { TRIAGE_KEY_PREFIX } from "../features/triage/api/triageApi";

/**
 * Everything that goes stale when companies are filed into a mandate.
 *
 * <p><b>Why this is not a page's own refresher.</b> `refreshScopedReads` on Strategy and
 * `refreshWhatMoved` on the Companies grid each know their own keys and close over the project they
 * are looking at. The assistant panel is mounted above the routes and files against
 * `proposal.projectId` — the mandate the tool call was authorised against, which is not necessarily
 * the mandate on screen. So the knowledge has to live somewhere above every feature, named for the
 * fact rather than for the caller.
 *
 * <p>A registry each feature opts into was the alternative and is worse: registration would happen
 * at import time, so a feature the session has not navigated to yet would silently not register —
 * a bug that only shows up on the page nobody was looking at.
 *
 * <p>The prefixes rather than the narrower keys, because every page, sort and filter variant spreads
 * into one — and the stage counts hang under {@link TRIAGE_KEY_PREFIX} for exactly this reason, so
 * the sidebar's badges move with the grid.
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
