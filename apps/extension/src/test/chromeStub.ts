import { vi } from "vitest";

/**
 * Enough of `chrome.*` for a panel hook to run under jsdom: listeners that can be fired, and a
 * `sendMessage` a test scripts per request kind.
 */
export interface ChromeStub {
  fireTabUpdated: (change: chrome.tabs.TabChangeInfo, tab?: Partial<chrome.tabs.Tab>) => void;
  fireTabActivated: () => void;
  fireStorageChanged: (changes: Record<string, chrome.storage.StorageChange>, area?: string) => void;
  answer: (handler: (request: { kind: string }) => unknown) => void;
}

export function installChromeStub(): ChromeStub {
  const tabUpdated = new Set<(id: number, change: chrome.tabs.TabChangeInfo, tab: chrome.tabs.Tab) => void>();
  const tabActivated = new Set<() => void>();
  const storageChanged = new Set<(changes: Record<string, chrome.storage.StorageChange>, area: string) => void>();
  let handler: (request: { kind: string }) => unknown = () => ({ ok: false, code: "UNSTUBBED", message: "no handler" });

  const listeners = <T>(set: Set<T>) => ({
    addListener: (fn: T) => set.add(fn),
    removeListener: (fn: T) => set.delete(fn),
  });

  vi.stubGlobal("chrome", {
    runtime: { sendMessage: async (request: { kind: string }) => handler(request) },
    tabs: { onUpdated: listeners(tabUpdated), onActivated: listeners(tabActivated) },
    storage: { onChanged: listeners(storageChanged) },
  });

  return {
    fireTabUpdated: (change, tab) =>
      tabUpdated.forEach((fn) => fn(1, change, { active: true, ...tab } as chrome.tabs.Tab)),
    fireTabActivated: () => tabActivated.forEach((fn) => fn()),
    fireStorageChanged: (changes, area = "local") => storageChanged.forEach((fn) => fn(changes, area)),
    answer: (next) => {
      handler = next;
    },
  };
}
