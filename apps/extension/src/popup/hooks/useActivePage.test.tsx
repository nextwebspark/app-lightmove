import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterAll, beforeEach, describe, expect, it, vi } from "vitest";
import { installChromeStub, type ChromeStub } from "../../test/chromeStub";
import { useActivePage } from "./useActivePage";

const AMIRA = "https://www.linkedin.com/in/amira-haddad/";
const BILAL = "https://www.linkedin.com/in/bilal-nasser/";

function readOf(url: string, fullName: string, pageKey: string) {
  return {
    ok: true,
    value: {
      subject: "person",
      person: { fullName, linkedinUrl: url },
      company: { companyName: null, linkedinUrl: null },
      sourceUrl: url,
      pageKey,
    },
  };
}

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe("following the page the panel is looking at", () => {
  let chrome: ChromeStub;

  beforeEach(() => {
    chrome = installChromeStub();
  });

  afterAll(() => {
    vi.unstubAllGlobals();
  });

  it("reads the page the worker says the panel is on", async () => {
    chrome.answer((request) =>
      request.kind === "activePageKey"
        ? { ok: true, value: { pageKey: "person:amira-haddad", sourceUrl: AMIRA } }
        : readOf(AMIRA, "Amira Haddad", "person:amira-haddad"),
    );

    const { result } = renderHook(() => useActivePage(), { wrapper });

    await waitFor(() => expect(result.current.person?.fullName).toBe("Amira Haddad"));
    expect(result.current.isReading).toBe(false);
  });

  it("offers nobody the moment the address changes, before any read has landed", async () => {
    chrome.answer((request) =>
      request.kind === "activePageKey"
        ? { ok: true, value: { pageKey: "person:amira-haddad", sourceUrl: AMIRA } }
        : readOf(AMIRA, "Amira Haddad", "person:amira-haddad"),
    );
    const { result } = renderHook(() => useActivePage(), { wrapper });
    await waitFor(() => expect(result.current.person?.fullName).toBe("Amira Haddad"));

    act(() => chrome.fireTabUpdated({ url: BILAL }));

    // Synchronously on the navigation: the previous profile is never on screen under the new address.
    expect(result.current.person).toBeNull();
    expect(result.current.isReading).toBe(true);
  });

  it("never shows a read that answered for the page the panel has already left", async () => {
    let release: (() => void) | null = null;
    const readWhenReleased = () =>
      new Promise((resolve) => {
        release = () => resolve(readOf(AMIRA, "Amira Haddad", "person:amira-haddad"));
      });
    chrome.answer((request) => {
      if (request.kind === "activePageKey") {
        return { ok: true, value: { pageKey: "person:amira-haddad", sourceUrl: AMIRA } };
      }
      return readWhenReleased();
    });

    const { result } = renderHook(() => useActivePage(), { wrapper });
    await waitFor(() => expect(release).not.toBeNull());

    act(() => chrome.fireTabUpdated({ url: BILAL }));
    (release as (() => void) | null)?.();

    await waitFor(() => expect(result.current.pageKey).toBe("person:bilal-nasser"));
    expect(result.current.person).toBeNull();
  });

  it("takes a later read of the same page, so a first answer can be corrected", async () => {
    let name = "Amira Haddad";
    chrome.answer((request) =>
      request.kind === "activePageKey"
        ? { ok: true, value: { pageKey: "person:bilal-nasser", sourceUrl: BILAL } }
        : readOf(BILAL, name, "person:bilal-nasser"),
    );
    const { result } = renderHook(() => useActivePage(), { wrapper });
    await waitFor(() => expect(result.current.person?.fullName).toBe("Amira Haddad"));

    name = "Bilal Nasser";
    act(() => chrome.fireTabUpdated({ title: "Bilal Nasser | LinkedIn" }));

    await waitFor(() => expect(result.current.person?.fullName).toBe("Bilal Nasser"));
  });

  it("adopts the page the worker actually read, rather than waiting for one it never will", async () => {
    chrome.answer((request) =>
      request.kind === "activePageKey"
        ? { ok: true, value: { pageKey: "offsite", sourceUrl: "https://example.com/" } }
        : readOf(BILAL, "Bilal Nasser", "person:bilal-nasser"),
    );

    const { result } = renderHook(() => useActivePage(), { wrapper });

    await waitFor(() => expect(result.current.pageKey).toBe("person:bilal-nasser"));
    await waitFor(() => expect(result.current.person?.fullName).toBe("Bilal Nasser"));
  });

  it("surfaces a refusal instead of reading forever", async () => {
    chrome.answer((request) =>
      request.kind === "activePageKey"
        ? { ok: true, value: { pageKey: "offsite", sourceUrl: "https://example.com/" } }
        : { ok: false, code: "LINKEDIN_ONLY", message: "LightMove Capture reads LinkedIn only, for now." },
    );

    const { result } = renderHook(() => useActivePage(), { wrapper });

    await waitFor(() => expect(result.current.readError?.code).toBe("LINKEDIN_ONLY"));
    expect(result.current.isReading).toBe(false);
    expect(result.current.hasReadOnce).toBe(true);
  });

  it("drops everything on screen when the consultant switches to another tab", async () => {
    chrome.answer((request) =>
      request.kind === "activePageKey"
        ? { ok: true, value: { pageKey: "person:amira-haddad", sourceUrl: AMIRA } }
        : readOf(AMIRA, "Amira Haddad", "person:amira-haddad"),
    );
    const { result } = renderHook(() => useActivePage(), { wrapper });
    await waitFor(() => expect(result.current.person?.fullName).toBe("Amira Haddad"));

    act(() => chrome.fireTabActivated());

    expect(result.current.person).toBeNull();
  });
});
