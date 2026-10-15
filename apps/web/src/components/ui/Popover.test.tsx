import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Popover } from "./Popover";

const stubTriggerRect = (left: number, top = 0) =>
  vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockReturnValue({
    top,
    left,
    right: left + 24,
    bottom: top + 24,
    width: 24,
    height: 24,
    x: left,
    y: top,
    toJSON: () => ({}),
  } as DOMRect);

const stubViewportWidth = (width: number) =>
  vi.spyOn(window, "innerWidth", "get").mockReturnValue(width);

const stubViewportHeight = (height: number) =>
  vi.spyOn(window, "innerHeight", "get").mockReturnValue(height);

describe("Popover", () => {
  afterEach(() => vi.restoreAllMocks());

  it("keeps a left-aligned panel on screen even when the trigger sits near a narrow viewport's left edge", async () => {
    // Narrower than width (190) + EDGE_GAP (12): the panel's own width no longer fits between the
    // trigger and the right edge, and clamping only against the right edge left `left` negative.
    stubViewportWidth(150);
    stubTriggerRect(0);
    render(
      <Popover label="menu" width={190} trigger={() => "open"}>
        {() => <div>content</div>}
      </Popover>,
    );

    await userEvent.click(screen.getByRole("button", { name: "menu" }));

    const panel = screen.getByText("content").parentElement as HTMLElement;
    expect(panel.style.left.replace("px", "")).not.toMatch(/^-/);
    expect(Number(panel.style.left.replace("px", ""))).toBeGreaterThanOrEqual(0);
  });

  it("still clamps a right-aligned panel against the left edge the same way", async () => {
    stubViewportWidth(150);
    stubTriggerRect(0);
    render(
      <Popover label="menu" align="right" width={190} trigger={() => "open"}>
        {() => <div>content</div>}
      </Popover>,
    );

    await userEvent.click(screen.getByRole("button", { name: "menu" }));

    const panel = screen.getByText("content").parentElement as HTMLElement;
    expect(Number(panel.style.left.replace("px", ""))).toBeGreaterThanOrEqual(0);
  });

  it("opens above a drawer and its scrim, so a picker inside one is seen", async () => {
    stubViewportWidth(1440);
    stubTriggerRect(100);
    render(
      <Popover label="menu" trigger={() => "open"}>
        {() => <div>content</div>}
      </Popover>,
    );

    await userEvent.click(screen.getByRole("button", { name: "menu" }));

    const panel = screen.getByText("content").parentElement as HTMLElement;
    const layer = Number(/z-\[(\d+)\]/.exec(panel.className)?.[1]);
    expect(layer).toBeGreaterThan(95);
  });

  it("opens an auto panel above a trigger near the foot of the window", async () => {
    stubViewportWidth(1440);
    stubViewportHeight(800);
    stubTriggerRect(100, 740);
    render(
      <Popover label="menu" placement="auto" trigger={() => "open"}>
        {() => <div>content</div>}
      </Popover>,
    );

    await userEvent.click(screen.getByRole("button", { name: "menu" }));

    const panel = screen.getByText("content").parentElement as HTMLElement;
    expect(panel.style.top).toBe("");
    expect(panel.style.bottom).toBe("66px");
  });

  it("still opens an auto panel below a trigger with room beneath it", async () => {
    stubViewportWidth(1440);
    stubViewportHeight(800);
    stubTriggerRect(100, 100);
    render(
      <Popover label="menu" placement="auto" trigger={() => "open"}>
        {() => <div>content</div>}
      </Popover>,
    );

    await userEvent.click(screen.getByRole("button", { name: "menu" }));

    const panel = screen.getByText("content").parentElement as HTMLElement;
    expect(panel.style.bottom).toBe("");
    expect(panel.style.top).toBe("130px");
  });
});
