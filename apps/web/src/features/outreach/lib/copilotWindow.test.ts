import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { closeCopilotWindow, openCopilotWindow, useCopilotWindowOpen } from "./copilotWindow";

const TARGET = { projectId: "p1", candidateId: "c1", recordingId: "r1" };

describe("openCopilotWindow", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    delete (window as { documentPictureInPicture?: unknown }).documentPictureInPicture;
  });

  it("floats the copilot over the call where the browser can, carrying the page's styles across", async () => {
    const floating = document.implementation.createHTMLDocument("pip");
    const pipWindow = { document: floating, close: vi.fn(), addEventListener: vi.fn() } as unknown as Window;
    const requestWindow = vi.fn().mockResolvedValue(pipWindow);
    (window as { documentPictureInPicture?: unknown }).documentPictureInPicture = { requestWindow };
    const style = document.createElement("style");
    style.textContent = ".x{}";
    document.head.appendChild(style);
    const open = vi.spyOn(window, "open");

    expect(await openCopilotWindow(TARGET)).toBe("floating");

    expect(requestWindow).toHaveBeenCalledWith({ width: 380, height: 620 });
    expect(floating.title).toBe("Interview copilot");
    expect(floating.head.querySelector("style")?.textContent).toBe(".x{}");
    expect(open).not.toHaveBeenCalled();
    style.remove();
  });

  it("opens the copilot's own page in a popup elsewhere, and says when the browser blocked it", async () => {
    const open = vi
      .spyOn(window, "open")
      .mockReturnValueOnce({} as Window)
      .mockReturnValueOnce(null);

    expect(await openCopilotWindow(TARGET)).toBe("popup");
    expect(open.mock.calls[0][0]).toBe("/copilot/p1/c1/r1");
    expect(String(open.mock.calls[0][2])).toContain("popup=yes");
    expect(await openCopilotWindow(TARGET)).toBe("blocked");
  });

  it("knows the copilot is in its window until the window goes, and closes it to bring it back", async () => {
    const floating = document.implementation.createHTMLDocument("pip");
    const target = new EventTarget();
    const pipWindow = {
      document: floating,
      close: vi.fn(() => target.dispatchEvent(new Event("pagehide"))),
      addEventListener: target.addEventListener.bind(target),
    } as unknown as Window;
    (window as { documentPictureInPicture?: unknown }).documentPictureInPicture = {
      requestWindow: vi.fn().mockResolvedValue(pipWindow),
    };
    // Another call than the popups above, which this module still counts as open.
    const { result } = renderHook(() => useCopilotWindowOpen("r9"));
    expect(result.current).toBe(false);

    await act(() => openCopilotWindow({ ...TARGET, recordingId: "r9" }));
    expect(result.current).toBe(true);

    act(() => closeCopilotWindow("r9"));
    expect(pipWindow.close).toHaveBeenCalled();
    expect(result.current).toBe(false);
  });

  it("takes the page's dark theme into the floating window", async () => {
    const floating = document.implementation.createHTMLDocument("pip");
    const pipWindow = { document: floating, close: vi.fn(), addEventListener: vi.fn() } as unknown as Window;
    (window as { documentPictureInPicture?: unknown }).documentPictureInPicture = {
      requestWindow: vi.fn().mockResolvedValue(pipWindow),
    };
    document.body.classList.add("dark");

    await openCopilotWindow(TARGET);

    expect(floating.body.classList.contains("dark")).toBe(true);
    document.body.classList.remove("dark");
  });
});
