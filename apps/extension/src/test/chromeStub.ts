import { vi } from "vitest";

/**
 * Enough of `chrome.*` for a panel hook to run under jsdom: listeners that can be fired, and a
 * `sendMessage` a test scripts per request kind.
 */
export interface ChromeStub {
  fireTabUpdated: (change: chrome.tabs.TabChangeInfo, tab?: Partial<chrome.tabs.Tab>) => void;
  fireTabActivated: (activated?: Partial<chrome.tabs.TabActiveInfo>) => void;
  fireStorageChanged: (changes: Record<string, chrome.storage.StorageChange>, area?: string) => void;
  answer: (handler: (request: { kind: string }) => unknown) => void;
}

/** `panelWindowId` docks the panel in a window, as `chrome.windows.getCurrent` reports it; left out, Chrome says nothing. */
export function installChromeStub({ panelWindowId }: { panelWindowId?: number } = {}): ChromeStub {
  const tabUpdated = new Set<(id: number, change: chrome.tabs.TabChangeInfo, tab: chrome.tabs.Tab) => void>();
  const tabActivated = new Set<(activated: chrome.tabs.TabActiveInfo) => void>();
  const storageChanged = new Set<(changes: Record<string, chrome.storage.StorageChange>, area: string) => void>();
  let handler: (request: { kind: string }) => unknown = () => ({ ok: false, code: "UNSTUBBED", message: "no handler" });

  const listeners = <T>(set: Set<T>) => ({
    addListener: (fn: T) => set.add(fn),
    removeListener: (fn: T) => set.delete(fn),
  });

  vi.stubGlobal("chrome", {
    runtime: { sendMessage: async (request: { kind: string }) => handler(request) },
    tabs: { onUpdated: listeners(tabUpdated), onActivated: listeners(tabActivated) },
    ...(panelWindowId === undefined ? {} : { windows: { getCurrent: async () => ({ id: panelWindowId }) } }),
    storage: { onChanged: listeners(storageChanged) },
  });

  return {
    fireTabUpdated: (change, tab) =>
      tabUpdated.forEach((fn) => fn(1, change, { active: true, ...tab } as chrome.tabs.Tab)),
    fireTabActivated: (activated) =>
      tabActivated.forEach((fn) => fn({ tabId: 1, windowId: panelWindowId ?? 1, ...activated })),
    fireStorageChanged: (changes, area = "local") => storageChanged.forEach((fn) => fn(changes, area)),
    answer: (next) => {
      handler = next;
    },
  };
}
