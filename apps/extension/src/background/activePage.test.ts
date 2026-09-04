import { describe, expect, it, vi } from "vitest";
import { activePageKey, readActivePage, type ActivePageDeps, type StoredLastRead } from "./activePage";
import type { PageSubject } from "../content/pageReader/readPageSubject";

const PROFILE_URL = "https://www.linkedin.com/in/bilal-nasser/details/experience/?trk=nav";

function personRead(fullName: string | null, pageUrl = PROFILE_URL): PageSubject {
  return {
    subject: "person",
    person: { fullName, linkedinUrl: null },
    company: { companyName: null, linkedinUrl: null },
    pageUrl,
    declaredUrl: null,
  };
}

function deps(
  overrides: {
    url?: string;
    reads?: (PageSubject | null)[];
    lastRead?: StoredLastRead | null;
  } = {},
): ActivePageDeps & { stored: () => StoredLastRead | null; injections: () => number } {
  const { url = PROFILE_URL, reads = [], lastRead = null } = overrides;
  let stored = lastRead;
  let injections = 0;
  return {
    queryTabs: async () => (url ? [{ id: 1, url, active: true } as chrome.tabs.Tab] : []),
    injectReader: async () => {
      const read = reads[Math.min(injections, reads.length - 1)] ?? null;
      injections += 1;
      return read;
    },
    lastRead: {
      get: async () => stored,
      set: async (value) => {
        stored = value;
      },
    },
    delay: async () => undefined,
    stored: () => stored,
    injections: () => injections,
  };
}

describe("reading the page the panel is looking at", () => {
  it("identifies a page by its slug, so a subpath and a tracking parameter are one page", async () => {
    const read = await readActivePage(deps({ reads: [personRead("Bilal Nasser")] }));

    expect(read.pageKey).toBe("person:bilal-nasser");
    // The address actually read is kept whole — it is what the capture records as its source.
    expect(read.sourceUrl).toBe(PROFILE_URL);
  });

  it("builds the captured URL from the address bar even when the page named nobody", async () => {
    const read = await readActivePage(deps({ reads: [personRead(null)] }));

    expect(read.person.fullName).toBeNull();
    expect(read.person.linkedinUrl).toBe("https://www.linkedin.com/in/bilal-nasser/");
  });

  it("refuses the previous profile's name rather than offering it for the new page", async () => {
    const page = deps({
      reads: [personRead("Amira Haddad")],
      lastRead: { tabId: 1, pageKey: "person:amira-haddad", name: "Amira Haddad" },
    });

    const read = await readActivePage(page);

    expect(read.person.fullName).toBeNull();
    expect(page.stored()?.name).toBe("Amira Haddad");
  });

  it("answers with the new name once the page catches up with its address", async () => {
    const page = deps({
      reads: [personRead("Amira Haddad"), personRead("Amira Haddad"), personRead("Bilal Nasser")],
      lastRead: { tabId: 1, pageKey: "person:amira-haddad", name: "Amira Haddad" },
    });

    const read = await readActivePage(page);

    expect(read.person.fullName).toBe("Bilal Nasser");
    expect(page.injections()).toBe(3);
    expect(page.stored()).toEqual({ tabId: 1, pageKey: "person:bilal-nasser", name: "Bilal Nasser" });
  });

  it("stops as soon as the tab has moved on again, rather than waiting out a page nobody is on", async () => {
    const page = deps({
      reads: [personRead("Someone Else", "https://www.linkedin.com/in/third-person/")],
      lastRead: { tabId: 1, pageKey: "person:amira-haddad", name: "Amira Haddad" },
    });

    const read = await readActivePage(page);

    expect(read.person.fullName).toBeNull();
    expect(page.injections()).toBe(1);
  });

  it("ignores a name remembered from another tab", async () => {
    const page = deps({
      reads: [personRead("Amira Haddad")],
      lastRead: { tabId: 99, pageKey: "person:amira-haddad", name: "Amira Haddad" },
    });

    expect((await readActivePage(page)).person.fullName).toBe("Amira Haddad");
  });

  it("refuses a LinkedIn page that names no person or company", async () => {
    await expect(readActivePage(deps({ url: "https://www.linkedin.com/feed/" }))).rejects.toThrow(
      /profile or company page/,
    );
  });

  it("refuses a tab that is not LinkedIn at all, pointing at the app instead", async () => {
    await expect(readActivePage(deps({ url: "https://example.com/" }))).rejects.toThrow(/reads LinkedIn only/);
  });
});

describe("telling the panel which page it is looking at", () => {
  it("answers without reading the page", async () => {
    const page = deps();

    expect(await activePageKey(page)).toEqual({ pageKey: "person:bilal-nasser", sourceUrl: PROFILE_URL });
    expect(page.injections()).toBe(0);
  });

  it("gives the two unreadable cases different keys, so their refusals do not stick", async () => {
    expect((await activePageKey(deps({ url: "https://www.linkedin.com/feed/" }))).pageKey).toBe("linkedin:unreadable");
    expect((await activePageKey(deps({ url: "https://example.com/" }))).pageKey).toBe("offsite");
  });
});

describe("choosing which tab to read", () => {
  it("falls back to the most recently touched LinkedIn tab when the focused window has none", async () => {
    const tabs = [
      { id: 2, url: "https://example.com/", lastAccessed: 5 },
      { id: 3, url: "https://www.linkedin.com/in/amira-haddad/", lastAccessed: 9 },
      { id: 4, url: "https://www.linkedin.com/in/older/", lastAccessed: 1 },
    ] as chrome.tabs.Tab[];
    const queryTabs = vi.fn(async (query: chrome.tabs.QueryInfo) => (query.active ? [tabs[0]] : tabs));

    const read = await readActivePage({
      ...deps({ reads: [personRead("Amira Haddad", "https://www.linkedin.com/in/amira-haddad/")] }),
      queryTabs,
    });

    expect(read.pageKey).toBe("person:amira-haddad");
  });
});
