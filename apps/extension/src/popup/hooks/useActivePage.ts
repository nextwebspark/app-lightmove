import { useQuery } from "@tanstack/react-query";
import { useCallback, useEffect, useRef, useState } from "react";
import { askServiceWorker, type ReadPageResult } from "../../background/extensionMessages";
import { tabPageKeyOf } from "../../content/pageReader/linkedInUrls";

/** A refusal to read, kept whole: `LINKEDIN_ONLY` renders as a pointer to the app, not an error. */
export class PageReadError extends Error {
  readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = "PageReadError";
    this.code = code;
  }
}

/** Coalesces the burst of tab events one navigation fires. Never delays the page *key*; see below. */
const SETTLE_BURST_MS = 150;

/**
 * What is on the page the consultant is looking at — the person, the company, and which of the two it
 * is about. One read feeds both tabs, and it follows the tab rather than freezing on whichever page
 * the toolbar gesture landed on.
 *
 * <b>The page key is the whole design.</b> The read is cached under the identity of the page it
 * describes, so the moment the address bar moves there is no entry for the new page and `data` is
 * `undefined` on that same render — the previous profile cannot be on screen while the next one is
 * read, and an answer for the page just left resolves into an entry nothing renders. The mask this
 * replaced could be cleared by whichever refetch happened to finish first, which is what made the
 * previous profile flash back.
 */
export function useActivePage() {
  const [pageKey, setPageKey] = useState<string | null>(null);
  const [hasReadOnce, setHasReadOnce] = useState(false);

  const page = useQuery<ReadPageResult>({
    queryKey: ["extension", "activePage", pageKey],
    enabled: pageKey !== null,
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

  // Which tab the worker reads is its own decision (`tabToRead` falls back off a focused DevTools
  // window), so the worker is asked rather than guessed at wherever the event does not carry a URL.
  // The sequence guards two answers racing: only the newest may set the key.
  const askedAt = useRef(0);
  const resolvePageKey = useCallback(async () => {
    askedAt.current += 1;
    const asked = askedAt.current;
    const result = await askServiceWorker({ kind: "activePageKey" });
    if (asked === askedAt.current && result.ok) {
      setPageKey(result.value.pageKey);
    }
  }, []);

  const { refetch } = page;
  useEffect(() => {
    void resolvePageKey();

    let settle: ReturnType<typeof setTimeout> | undefined;
    const rereadSoon = () => {
      clearTimeout(settle);
      settle = setTimeout(() => void refetch(), SETTLE_BURST_MS);
    };

    // LinkedIn navigates with pushState: `url` fires before the new profile has rendered, `status:
    // complete` never fires at all, and `title` fires once the new page has mounted its content. The
    // key moves on `url` with no debounce at all — delaying it by even the coalescing window is that
    // much longer with the previous person's name on screen — while the read that follows is
    // debounced, because one move is a burst and only its last event is worth reading after.
    const onUpdated = (_id: number, change: chrome.tabs.TabChangeInfo, tab: chrome.tabs.Tab) => {
      if (!tab.active) {
        return;
      }
      if (change.url) {
        setPageKey(tabPageKeyOf(change.url));
        askedAt.current += 1;
      } else if (change.status === "complete" || change.title) {
        rereadSoon();
      }
    };
    // Only ids, so the worker is asked — and the key is dropped first, because everything on screen
    // belongs to a tab nobody is looking at any more.
    const onActivated = () => {
      setPageKey(null);
      void resolvePageKey();
    };

    chrome.tabs.onActivated.addListener(onActivated);
    chrome.tabs.onUpdated.addListener(onUpdated);
    return () => {
      clearTimeout(settle);
      chrome.tabs.onActivated.removeListener(onActivated);
      chrome.tabs.onUpdated.removeListener(onUpdated);
    };
  }, [refetch, resolvePageKey]);

  // The worker resolved a different tab than the panel guessed. It is the authority — adopting its key
  // rather than discarding the answer is what stops the panel sitting empty for a page it can see.
  // It cannot loop: the read taken under the adopted key reports that same key.
  const answered = page.data;
  useEffect(() => {
    if (answered && answered.pageKey !== pageKey) {
      setPageKey(answered.pageKey);
    }
  }, [answered, pageKey]);

  const isCurrent = Boolean(answered) && answered?.pageKey === pageKey;
  const read = isCurrent ? answered : undefined;
  // A refused page has been answered as surely as a read one — the panel is not still reading it, and
  // the refusal must not sit behind a loader.
  const hasAnswered = isCurrent || page.isError;

  useEffect(() => {
    if (hasAnswered) {
      setHasReadOnce(true);
    }
  }, [hasAnswered]);

  return {
    subject: read?.subject ?? null,
    person: read?.person ?? null,
    company: read?.company ?? null,
    sourceUrl: read?.sourceUrl ?? null,
    pageKey,
    isReading: pageKey === null || page.isFetching || !hasAnswered,
    /** The very first read of the panel's life, the one case with no form worth leaving on screen. */
    hasReadOnce,
    readError: page.error instanceof PageReadError ? page.error : null,
    rescan: async () => {
      await resolvePageKey();
      await refetch();
    },
  };
}

export type ActivePage = ReturnType<typeof useActivePage>;
