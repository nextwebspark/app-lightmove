import { describe, expect, it, vi } from "vitest";
import { applyToolbarIcon, watchColorScheme } from "./toolbarIcon";

function deps({ hasDocument = false } = {}) {
  return {
    hasOffscreenDocument: vi.fn(async () => hasDocument),
    createOffscreenDocument: vi.fn(async () => undefined),
    setIcon: vi.fn(async () => undefined),
  };
}

describe("the toolbar icon's ink", () => {
  it("is white on a dark browser", async () => {
    const chrome = deps();

    await applyToolbarIcon(chrome, true);

    expect(chrome.setIcon).toHaveBeenCalledWith({
      path: { 16: "icons/uncava-app-icon-dark-16.png", 32: "icons/uncava-app-icon-dark-32.png" },
    });
  });

  it("is black on a light browser", async () => {
    const chrome = deps();

    await applyToolbarIcon(chrome, false);

    expect(chrome.setIcon).toHaveBeenCalledWith({
      path: { 16: "icons/uncava-app-icon-light-16.png", 32: "icons/uncava-app-icon-light-32.png" },
    });
  });

  it("opens the colour-scheme watcher when none is running", async () => {
    const chrome = deps();

    await watchColorScheme(chrome);

    expect(chrome.createOffscreenDocument).toHaveBeenCalledWith(
      expect.objectContaining({ url: "color-scheme.html", reasons: ["MATCH_MEDIA"] }),
    );
  });

  it("does not open a second watcher over a running one", async () => {
    const chrome = deps({ hasDocument: true });

    await watchColorScheme(chrome);

    expect(chrome.createOffscreenDocument).not.toHaveBeenCalled();
  });

  it("leaves the worker standing when Chrome refuses the watcher", async () => {
    const chrome = deps();
    chrome.createOffscreenDocument.mockRejectedValue(new Error("Only a single offscreen document may be created."));

    await expect(watchColorScheme(chrome)).resolves.toBeUndefined();
  });
});
