import { useQuery } from "@tanstack/react-query";
import { askServiceWorker, type ReadPageResult } from "../../background/extensionMessages";

const ACTIVE_PAGE_KEY = ["extension", "activePage"] as const;

/**
 * What is on the page the consultant invoked the extension from — the person, the company, and which
 * of the two the page is about.
 *
 * One read for both tabs: the reader is injected once per open, so switching tabs costs nothing and a
 * person still carries the employer the company side found.
 *
 * `staleTime: 0` and no caching between opens on purpose: the whole point is what is on screen *now*,
 * and a remembered read would quietly offer the previous tab's company. `rescan` is the design's
 * "Re-scan" link, for the pages that finish rendering after the popup opened.
 */
export function useActivePage() {
  const page = useQuery<ReadPageResult>({
    queryKey: ACTIVE_PAGE_KEY,
    staleTime: 0,
    gcTime: 0,
    queryFn: async () => {
      const result = await askServiceWorker({ kind: "readActivePage" });
      if (!result.ok) {
        throw new Error(result.message);
      }
      return result.value;
    },
  });

  return {
    subject: page.data?.subject ?? null,
    person: page.data?.person ?? null,
    company: page.data?.company ?? null,
    sourceUrl: page.data?.sourceUrl ?? null,
    isReading: page.isFetching,
    readError: page.error instanceof Error ? page.error.message : null,
    rescan: page.refetch,
  };
}

export type ActivePage = ReturnType<typeof useActivePage>;
