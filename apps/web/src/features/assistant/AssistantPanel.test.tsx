import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { AssistantProvider } from "./AssistantProvider";
import { AssistantLauncher } from "./components/AssistantLauncher";
import { AssistantPanel } from "./components/AssistantPanel";

/**
 * That the assistant opens, closes, and never takes the page hostage while it is open.
 *
 * <p>The non-modal part is the one worth a test: the whole point of docking it is that a consultant
 * keeps ticking rows in the grid beside it, and `Drawer`'s `aria-modal` would tell a screen reader
 * the rest of the page is gone.
 */
function mount() {
  return render(
    <AssistantProvider>
      <AssistantLauncher />
      <AssistantPanel contextLabel="Meridian Energy Group · CFO" />
    </AssistantProvider>,
  );
}

describe("the assistant panel", () => {
  beforeEach(() => localStorage.clear());

  // Both the launcher and the panel are in the DOM and hidden by CSS below `lg`; jsdom applies no
  // CSS, so these assert what is rendered, and `node e2e/spa/responsive.mjs` covers the widths.
  it("starts shut, behind a launcher", () => {
    mount();

    expect(screen.getByRole("button", { name: /ask/i })).toBeInTheDocument();
    expect(screen.queryByRole("complementary", { name: "Uncava Assistant" })).not.toBeInTheDocument();
  });

  it("opens from the launcher, which then stands down", async () => {
    mount();

    await userEvent.click(screen.getByRole("button", { name: /ask/i }));

    expect(screen.getByRole("complementary", { name: "Uncava Assistant" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^ask$/i })).not.toBeInTheDocument();
  });

  it("is a complementary region and not a modal dialog", async () => {
    mount();

    await userEvent.click(screen.getByRole("button", { name: /ask/i }));

    const panel = screen.getByRole("complementary", { name: "Uncava Assistant" });
    // A scrim and aria-modal are what make Drawer right for one record and wrong for this: the grid
    // beside it has to stay clickable and reachable.
    expect(panel).not.toHaveAttribute("aria-modal");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("names the mandate the conversation is about", async () => {
    mount();

    await userEvent.click(screen.getByRole("button", { name: /ask/i }));

    expect(screen.getByText("Meridian Energy Group · CFO")).toBeInTheDocument();
  });

  it("closes on Escape and gives the launcher back", async () => {
    mount();
    await userEvent.click(screen.getByRole("button", { name: /ask/i }));

    await userEvent.keyboard("{Escape}");

    expect(screen.queryByRole("complementary", { name: "Uncava Assistant" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /ask/i })).toBeInTheDocument();
  });

  it("remembers being open across a remount, because the layouts are siblings", async () => {
    const first = mount();
    await userEvent.click(screen.getByRole("button", { name: /ask/i }));
    first.unmount();

    mount();

    expect(screen.getByRole("complementary", { name: "Uncava Assistant" })).toBeInTheDocument();
  });

  it("puts a starter into the composer rather than sending it", async () => {
    mount();
    await userEvent.click(screen.getByRole("button", { name: /ask/i }));

    await userEvent.click(screen.getByRole("button", { name: /Top 10 IPP operators/i }));

    expect(screen.getByRole("textbox", { name: "Ask the assistant" })).toHaveValue(
      "Top 10 IPP operators in Saudi Arabia",
    );
  });
});
