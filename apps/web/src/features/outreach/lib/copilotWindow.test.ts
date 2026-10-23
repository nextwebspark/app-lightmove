import { afterEach, describe, expect, it, vi } from "vitest";
import { openCopilotWindow } from "./copilotWindow";

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
});
