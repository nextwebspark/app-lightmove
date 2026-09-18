import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";
import { ExpandableText } from "./ExpandableText";

const DESCRIPTION =
  "Saudi Aramco is the largest integrated energy and chemicals company in the world, operating " +
  "across upstream, downstream and chemicals from Dhahran.";

/**
 * jsdom lays nothing out, so `scrollHeight` and `clientHeight` are both 0 and every paragraph looks
 * un-clipped. Stubbing the two properties the component actually asks about is the whole reason this
 * test can distinguish the two cases at all.
 */
function measureAs(scrollHeight: number, clientHeight: number) {
  Object.defineProperty(HTMLElement.prototype, "scrollHeight", { configurable: true, value: scrollHeight });
  Object.defineProperty(HTMLElement.prototype, "clientHeight", { configurable: true, value: clientHeight });
}

afterEach(() => {
  Object.defineProperty(HTMLElement.prototype, "scrollHeight", { configurable: true, value: 0 });
  Object.defineProperty(HTMLElement.prototype, "clientHeight", { configurable: true, value: 0 });
});

describe("ExpandableText", () => {
  it("offers the rest of a description that does not fit", async () => {
    measureAs(220, 104);
    render(<ExpandableText text={DESCRIPTION} />);

    const toggle = screen.getByRole("button", { name: "Show more" });
    expect(screen.getByText(DESCRIPTION)).toHaveStyle({ WebkitLineClamp: "5" });

    await userEvent.click(toggle);

    // The clamp is gone, so the whole description is on the page and the control reads the other way.
    expect(screen.getByText(DESCRIPTION)).not.toHaveStyle({ WebkitLineClamp: "5" });
    expect(screen.getByRole("button", { name: "Show less" })).toHaveAttribute("aria-expanded", "true");
  });

  it("stays quiet when the description already fits", () => {
    measureAs(48, 104);
    render(<ExpandableText text="A regional contractor." />);

    // A "Show more" that opens nothing is worse than no control at all.
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("clamps to the lines it was asked for", () => {
    measureAs(220, 62);
    render(<ExpandableText text={DESCRIPTION} lines={3} />);

    expect(screen.getByText(DESCRIPTION)).toHaveStyle({ WebkitLineClamp: "3" });
  });
});
