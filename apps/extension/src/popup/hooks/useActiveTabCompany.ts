import { useQuery } from "@tanstack/react-query";
import { askServiceWorker, type ReadPageResult } from "../../background/extensionMessages";

const ACTIVE_TAB_KEY = ["extension", "activeTabCompany"] as const;

/**
 * The company on the page the consultant invoked the extension from.
 *
 * `staleTime: 0` and no caching between opens on purpose: the whole point is what is on screen *now*,
 * and a remembered read would quietly offer the previous tab's company. `rescan` is the design's
 * "Re-scan" link, for the pages that finish rendering after the popup opened.
 */
export function useActiveTabCompany() {
  const page = useQuery<ReadPageResult>({
    queryKey: ACTIVE_TAB_KEY,
    staleTime: 0,
    gcTime: 0,
    queryFn: async () => {
      const result = await askServiceWorker({ kind: "readActiveTabCompany" });
      if (!result.ok) {
        throw new Error(result.message);
      }
      return result.value;
    },
  });

  return {
    company: page.data?.company ?? null,
    sourceUrl: page.data?.sourceUrl ?? null,
    isReading: page.isFetching,
    readError: page.error instanceof Error ? page.error.message : null,
    rescan: page.refetch,
  };
}
