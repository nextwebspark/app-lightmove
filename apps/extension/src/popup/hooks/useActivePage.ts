import { useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
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
  //
  // `hasLeftThePage` is what stops the panel showing the previous profile's name over the new one:
  // React Query holds the last answer while it refetches, so between the navigation and the read
  // landing the form still renders whoever was captured before — the consultant themselves, when
  // they came from their own profile. It is set the instant the tab reports a new address and
  // cleared by the read that answers for it.
  const { refetch } = page;
  const [hasLeftThePage, setHasLeftThePage] = useState(false);
  useEffect(() => {
    let settle: ReturnType<typeof setTimeout> | undefined;
    // Debounced, because one profile-to-profile move is a burst of events — and the burst's *last*
    // event is the one to read after. LinkedIn navigates with pushState: `url` fires before the new
    // profile has rendered (an immediate read captures the previous person), `status: complete` never
    // fires at all, and `title` fires once the new page has actually mounted its content.
    // Only a changed address means the panel is looking at something else. A title that changed on
    // the same page is LinkedIn counting notifications, or finishing its render — worth re-reading,
    // never worth blanking a field the consultant may be typing in.
    const reread = (leavingThePage: boolean) => {
      if (leavingThePage) {
        setHasLeftThePage(true);
      }
      clearTimeout(settle);
      settle = setTimeout(() => {
        void refetch().finally(() => setHasLeftThePage(false));
      }, 400);
    };
    const onActivated = () => reread(true);
    const onUpdated = (_id: number, change: chrome.tabs.TabChangeInfo, tab: chrome.tabs.Tab) => {
      if (!tab.active) {
        return;
      }
      if (change.url) {
        reread(true);
      } else if (change.status === "complete" || change.title) {
        reread(false);
      }
    };
    chrome.tabs.onActivated.addListener(onActivated);
    chrome.tabs.onUpdated.addListener(onUpdated);
    return () => {
      clearTimeout(settle);
      chrome.tabs.onActivated.removeListener(onActivated);
      chrome.tabs.onUpdated.removeListener(onUpdated);
    };
  }, [refetch]);

  // Nothing about the page the consultant has left is true any more, so none of it is offered.
  const read = hasLeftThePage ? undefined : page.data;

  return {
    subject: read?.subject ?? null,
    person: read?.person ?? null,
    company: read?.company ?? null,
    sourceUrl: read?.sourceUrl ?? null,
    isReading: page.isFetching || hasLeftThePage,
    readError: page.error instanceof PageReadError ? page.error : null,
    rescan: page.refetch,
  };
}

export type ActivePage = ReturnType<typeof useActivePage>;
