import { mergeExtracted } from "../content/pageReader/extractedCompany";
import { mergeExtractedPerson } from "../content/pageReader/extractedPerson";
import {
  isLinkedInPageUrl,
  linkedInCompanyUrlOf,
  linkedInProfileUrlOf,
  pageKeyOf,
  tabPageKeyOf,
} from "../content/pageReader/linkedInUrls";
import {
  nameReadFrom,
  settleEvidenceOf,
  type PreviousRead,
} from "../content/pageReader/pageSettleEvidence";
import type { PageSubject } from "../content/pageReader/readPageSubject";
import type { ActivePageKey, ReadPageResult } from "./extensionMessages";

/**
 * Reading the tab the panel is looking at, with the tab resolution and the staleness rule in one place
 * so a test can drive both without a browser. `serviceWorker.ts` supplies the real `chrome.*`.
 */
export interface ActivePageDeps {
  queryTabs: (query: chrome.tabs.QueryInfo) => Promise<chrome.tabs.Tab[]>;
  /** One injection of the page reader; null when the tab could not be read at all. */
  injectReader: (tabId: number) => Promise<PageSubject | null>;
  lastRead: {
    get: () => Promise<StoredLastRead | null>;
    set: (value: StoredLastRead) => Promise<void>;
  };
  delay: (ms: number) => Promise<void>;
}

/** The last confident read, kept per tab: a name only means "stale" against the page it was read at. */
export interface StoredLastRead extends PreviousRead {
  tabId: number;
}

const POLL_EVERY_MS = 150;

/**
 * How long a read may wait for the page to catch up with its address, as attempts rather than a clock —
 * ~3s, which covers LinkedIn's usual 200-900ms mount with room for a slow connection. Past it the read
 * is answered with an empty name rather than the previous profile's, and the re-read that follows
 * LinkedIn's title change fills it in.
 */
const SETTLE_ATTEMPTS = 20;

/** Which page the panel is looking at, answered without injecting anything. */
export async function activePageKey(deps: ActivePageDeps): Promise<ActivePageKey> {
  const tab = await tabToRead(deps);
  return { pageKey: tabPageKeyOf(tab?.url), sourceUrl: tab?.url ?? "" };
}

/**
 * Reads the tab the consultant invoked the extension on.
 *
 * LinkedIn only. The URL decides the subject and supplies the one field that must never be missing —
 * the canonical profile or company URL, built from the address bar's slug — so even a page whose DOM
 * yields nothing still captures with its URL, and the consultant types the name. Anything richer
 * than name + URL is enrichment, done server-side later, not read off the page.
 */
export async function readActivePage(deps: ActivePageDeps): Promise<ReadPageResult> {
  const tab = await tabToRead(deps);
  if (!tab?.id) {
    throw new LinkedInOnlyError();
  }
  const sourceUrl = tab.url ?? "";
  const pageKey = pageKeyOf(sourceUrl);
  if (!pageKey) {
    // LinkedIn, but not a page that names a person or a company — the feed, search, jobs.
    throw new PageNotReadableError();
  }

  const read = await readWhenSettled(deps, tab.id, pageKey);
  const profileSlug = pageKey.startsWith("person:") ? pageKey.slice("person:".length) : null;

  if (profileSlug) {
    // The slug-built URL first, so it wins the merge: the address bar cannot lie, and it is present
    // even when the read came back empty.
    return {
      subject: "person",
      person: mergeExtractedPerson([
        { linkedinUrl: linkedInProfileUrlOf(profileSlug) },
        read?.person ?? {},
      ]),
      company: mergeExtracted([]),
      sourceUrl,
      pageKey,
    };
  }

  const companySlug = pageKey.slice("company:".length);
  return {
    subject: "company",
    person: mergeExtractedPerson([]),
    company: mergeExtracted([
      { linkedinUrl: linkedInCompanyUrlOf(companySlug) },
      read?.company ?? {},
    ]),
    sourceUrl,
    pageKey,
  };
}

/**
 * Re-reads until the page stops looking like the one it was navigated away from, and answers with an
 * empty name if it never does.
 *
 * Polling rather than one shot is the whole point: a single read taken while LinkedIn is still
 * swapping its content returns the previous person under the new address, which is what put someone
 * else's name above the Save button. See `settleEvidenceOf` for what "still" means.
 */
async function readWhenSettled(
  deps: ActivePageDeps,
  tabId: number,
  pageKey: string,
): Promise<PageSubject | null> {
  const stored = await deps.lastRead.get();
  const previous = stored?.tabId === tabId ? stored : null;

  let read: PageSubject | null = null;
  for (let attempt = 0; attempt < SETTLE_ATTEMPTS; attempt += 1) {
    if (attempt > 0) {
      await deps.delay(POLL_EVERY_MS);
    }
    const attempted = await deps.injectReader(tabId);
    if (!attempted) {
      continue;
    }
    if (pageKeyOf(attempted.pageUrl) !== pageKey) {
      // Navigated on again while this was in flight. The panel has already moved to the new page's
      // key and will discard this answer, so there is nothing left to wait for.
      return null;
    }
    read = attempted;
    if (settleEvidenceOf(read, previous) === "settled") {
      const name = nameReadFrom(read);
      if (name) {
        // Only a settled read is remembered. Recording a blank would let the next read fall through
        // to "nothing against it" and hand the stale name straight back.
        await deps.lastRead.set({ tabId, pageKey, name });
      }
      return read;
    }
  }

  return read ? withoutName(read) : null;
}

/** The read minus what it claimed to be looking at — the URLs it carries are still the address bar's. */
function withoutName(read: PageSubject): PageSubject {
  return {
    ...read,
    person: { ...read.person, fullName: null },
    company: { ...read.company, companyName: null },
  };
}

/**
 * The tab the panel is looking at.
 *
 * `currentWindow` is the last *focused* window, which is not always a browser window: with DevTools
 * detached and focused it resolves to something with no readable tab, and the read would be refused
 * for a page sitting right there. Falling back to the most recently touched LinkedIn tab answers
 * for the tab beside it — and null means the consultant has no LinkedIn page open anywhere, which
 * is the LinkedIn-only message's case, not an error.
 */
async function tabToRead(deps: ActivePageDeps): Promise<chrome.tabs.Tab | null> {
  const [inCurrent] = await deps.queryTabs({ active: true, currentWindow: true });
  if (inCurrent?.id && isCapturableTab(inCurrent)) {
    return inCurrent;
  }
  const all = await deps.queryTabs({});
  return (
    all
      .filter(isCapturableTab)
      .sort((a, b) => (b.lastAccessed ?? 0) - (a.lastAccessed ?? 0))[0] ?? null
  );
}

/** A tab worth reading: LinkedIn, the one site this plugin captures from. */
function isCapturableTab(tab: chrome.tabs.Tab): boolean {
  return Boolean(tab.id) && isLinkedInPageUrl(tab.url);
}

/** Not on LinkedIn at all — answered with the pointer to the app, not with an apology. */
export class LinkedInOnlyError extends Error {
  constructor() {
    super(
      "LightMove Capture reads LinkedIn only, for now. To add a person or a company by hand, open LightMove.",
    );
    this.name = "LinkedInOnlyError";
  }
}

export class PageNotReadableError extends Error {
  constructor() {
    super("Open a LinkedIn profile or company page to capture from it.");
    this.name = "PageNotReadableError";
  }
}
