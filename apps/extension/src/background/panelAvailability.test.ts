import { describe, expect, it, vi } from "vitest";
import {
  applyPanelAvailability,
  applyPanelAvailabilityToAllTabs,
  disableGlobalPanel,
} from "./panelAvailability";

function deps(tabs: { id?: number; url?: string }[] = []) {
  return {
    queryTabs: vi.fn(async () => tabs as chrome.tabs.Tab[]),
    setPanelOptions: vi.fn(async () => undefined),
    enableAction: vi.fn(async () => undefined),
    disableAction: vi.fn(async () => undefined),
    setActionTitle: vi.fn(async () => undefined),
  };
}

describe("where LightMove Capture offers itself", () => {
  it("shows the panel and lights the icon on a LinkedIn page", async () => {
    const chrome = deps();

    await applyPanelAvailability(chrome, { id: 1, url: "https://www.linkedin.com/in/amira-haddad/" });

    // The path goes with the enable, always: without it the tab is enabled and points at nothing, the
    // icon lights up, and clicking it opens no panel. That shipped once.
    expect(chrome.setPanelOptions).toHaveBeenCalledWith({ tabId: 1, enabled: true, path: "popup.html" });
    expect(chrome.enableAction).toHaveBeenCalledWith(1);
    expect(chrome.setActionTitle).toHaveBeenCalledWith({ tabId: 1, title: "LightMove Capture" });
  });

  it("hides the panel and greys the icon everywhere else", async () => {
    const chrome = deps();

    await applyPanelAvailability(chrome, { id: 2, url: "https://mail.google.com/" });

    expect(chrome.setPanelOptions).toHaveBeenCalledWith({ tabId: 2, enabled: false });
    expect(chrome.disableAction).toHaveBeenCalledWith(2);
    // A greyed icon whose click does nothing needs to say why on hover, or it reads as broken.
    expect(chrome.setActionTitle).toHaveBeenCalledWith({
      tabId: 2,
      title: "LightMove Capture — open a LinkedIn profile or company page",
    });
  });

  it("treats a tab with no address yet as unavailable rather than leaving it alone", async () => {
    const chrome = deps();

    // Skipping it would leave the manifest's enabled-everywhere default standing, and the panel would
    // show on every freshly opened tab.
    await applyPanelAvailability(chrome, { id: 3 });

    expect(chrome.setPanelOptions).toHaveBeenCalledWith({ tabId: 3, enabled: false });
    expect(chrome.disableAction).toHaveBeenCalledWith(3);
  });

  it("does not offer itself on the workspace's own pages — LinkedIn is the one site it reads", async () => {
    const chrome = deps();

    await applyPanelAvailability(chrome, { id: 4, url: "http://localhost:5173/extension/connect" });

    expect(chrome.setPanelOptions).toHaveBeenCalledWith({ tabId: 4, enabled: false });
  });

  it("survives a tab that closed while it was being judged", async () => {
    const chrome = deps();
    chrome.setPanelOptions.mockRejectedValue(new Error("No tab with id 5."));
    chrome.disableAction.mockRejectedValue(new Error("No tab with id 5."));
    chrome.setActionTitle.mockRejectedValue(new Error("No tab with id 5."));

    await expect(applyPanelAvailability(chrome, { id: 5, url: "https://example.com/" })).resolves.toBeUndefined();
  });

  it("turns the panel that belongs to no tab off, since a global one overrides every per-tab rule", async () => {
    const chrome = deps();

    await disableGlobalPanel(chrome);

    // No tabId: this is the global panel, the one that would otherwise show on every site whatever
    // the per-tab options said.
    expect(chrome.setPanelOptions).toHaveBeenCalledWith({ enabled: false });
  });

  it("judges every tab already open, not only the one in front", async () => {
    const chrome = deps([
      { id: 1, url: "https://www.linkedin.com/in/amira-haddad/" },
      { id: 2, url: "https://news.example/" },
    ]);

    await applyPanelAvailabilityToAllTabs(chrome);

    expect(chrome.setPanelOptions).toHaveBeenCalledWith({ tabId: 1, enabled: true, path: "popup.html" });
    expect(chrome.setPanelOptions).toHaveBeenCalledWith({ tabId: 2, enabled: false });
  });
});
