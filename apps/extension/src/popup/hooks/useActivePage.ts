import { useQuery } from "@tanstack/react-query";
import { useEffect } from "react";
import { askServiceWorker, type ReadPageResult } from "../../background/extensionMessages";

/** A refusal to read, kept whole: `LINKEDIN_ONLY` renders as a pointer to the app, not an error. */
export class PageReadError extends Error {
  readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = "PageReadError";
    this.code = code;
  }
}

const ACTIVE_PAGE_KEY = ["extension", "activePage"] as const;

/**
 * What is on the page the consultant invoked the extension from — the person, the company, and which
 * of the two it is about. One read feeds both tabs.
 *
 * Uncached on purpose: a remembered read would offer the previous tab's company.
 */
export function useActivePage() {
  const page = useQuery<ReadPageResult>({
    queryKey: ACTIVE_PAGE_KEY,
    staleTime: 0,
    gcTime: 0,
    queryFn: async () => {
      const result = await askServiceWorker({ kind: "readActivePage" });
      if (!result.ok) {
        throw new PageReadError(result.code, result.message);
      }
      return result.value;
    },
  });

  // The panel stays open while the consultant moves around, so the read follows the tab rather than
  // freezing on whichever page the toolbar gesture landed on.
  const { refetch } = page;
  useEffect(() => {
    let settle: ReturnType<typeof setTimeout> | undefined;
    // Debounced, because one profile-to-profile move is a burst of events — and the burst's *last*
    // event is the one to read after. LinkedIn navigates with pushState: `url` fires before the new
    // profile has rendered (an immediate read captures the previous person), `status: complete` never
    // fires at all, and `title` fires once the new page has actually mounted its content.
    const reread = () => {
      clearTimeout(settle);
      settle = setTimeout(() => void refetch(), 400);
    };
    const onUpdated = (_id: number, change: chrome.tabs.TabChangeInfo, tab: chrome.tabs.Tab) => {
      if (tab.active && (change.status === "complete" || change.url || change.title)) {
        reread();
      }
    };
    chrome.tabs.onActivated.addListener(reread);
    chrome.tabs.onUpdated.addListener(onUpdated);
    return () => {
      clearTimeout(settle);
      chrome.tabs.onActivated.removeListener(reread);
      chrome.tabs.onUpdated.removeListener(onUpdated);
    };
  }, [refetch]);

  return {
    subject: page.data?.subject ?? null,
    person: page.data?.person ?? null,
    company: page.data?.company ?? null,
    sourceUrl: page.data?.sourceUrl ?? null,
    isReading: page.isFetching,
    readError: page.error instanceof PageReadError ? page.error : null,
    rescan: page.refetch,
  };
}

export type ActivePage = ReturnType<typeof useActivePage>;
