import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";
import { TruncatedText } from "./TruncatedText";

const LONG = "Saudi Aramco is the largest integrated energy and chemicals company in the world.";

/**
 * jsdom lays nothing out, so `scrollWidth` and `clientWidth` are both 0 and every string looks
 * un-clipped. Stubbing the two properties the component actually asks about is the whole reason
 * this test can distinguish the two cases at all.
 */
function measureAs(scrollWidth: number, clientWidth: number) {
  Object.defineProperty(HTMLElement.prototype, "scrollWidth", { configurable: true, value: scrollWidth });
  Object.defineProperty(HTMLElement.prototype, "clientWidth", { configurable: true, value: clientWidth });
}

afterEach(() => {
  Object.defineProperty(HTMLElement.prototype, "scrollWidth", { configurable: true, value: 0 });
  Object.defineProperty(HTMLElement.prototype, "clientWidth", { configurable: true, value: 0 });
});

describe("TruncatedText", () => {
  it("reveals the whole value when the line is cut", async () => {
    measureAs(420, 130);
    render(<TruncatedText value={LONG} />);

    await userEvent.hover(screen.getByText(LONG));

    // Two nodes now carry the text: the clipped line and the bubble over it.
    expect(screen.getByRole("tooltip")).toHaveTextContent(LONG);
  });

  it("stays quiet when the value already fits", async () => {
    measureAs(90, 130);
    render(<TruncatedText value="Dubai" />);

    await userEvent.hover(screen.getByText("Dubai"));

    // A tooltip repeating a value that is fully visible is noise, and in a table it is constant
    // noise — thirty cells a row, every one of them armed.
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
  });

  it("dismisses on leave", async () => {
    measureAs(420, 130);
    render(<TruncatedText value={LONG} />);

    // Captured before hovering: once the bubble is up, two nodes carry this text.
    const line = screen.getByText(LONG);
    await userEvent.hover(line);
    await userEvent.unhover(line);

    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
  });

  it("renders a missing value as a dash rather than an empty cell", () => {
    render(<TruncatedText value={null} />);

    // An empty cell reads as a rendering failure; a dash reads as "the universe does not know".
    expect(screen.getByText("—")).toBeInTheDocument();
  });
});
