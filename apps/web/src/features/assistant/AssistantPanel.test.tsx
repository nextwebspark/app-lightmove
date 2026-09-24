import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { AssistantProvider, useAssistant } from "./AssistantProvider";
import { AssistantDock } from "./components/AssistantDock";

/**
 * That the assistant opens, closes, and never takes the page hostage while it is open.
 *
 * <p>The non-modal part is the one worth a test: the whole point of docking it is that a consultant
 * keeps ticking rows in the grid beside it, and `Drawer`'s `aria-modal` would tell a screen reader
 * the rest of the page is gone.
 *
 * <p>Mounted through {@link AssistantDock}, which is what a screen renders. A shut panel is behind
 * `aria-hidden` rather than absent — it stays in the tree long enough to animate out — so the
 * queries below say the same thing they always did.
 */
/** Strategy's AI Research button is the only way in, and it does no more than this. */
function AiResearchButton() {
  const { openAssistant } = useAssistant();
  return (
    <button type="button" onClick={() => openAssistant("p1")}>
      AI Research
    </button>
  );
}

/** A fresh client per test, so one test's in-flight mutation cannot answer the next one's. */
function mount(projectId = "p1") {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <AssistantProvider>
        <AiResearchButton />
        <AssistantDock contextLabel="Meridian Energy Group · CFO" projectId={projectId} />
      </AssistantProvider>
    </QueryClientProvider>,
  );
}

describe("the assistant panel", () => {
  beforeEach(() => localStorage.clear());

  it("starts shut", () => {
    mount();

    expect(screen.queryByRole("complementary", { name: "Uncava Assistant" })).not.toBeInTheDocument();
  });

  it("opens from AI Research", async () => {
    mount();

    await userEvent.click(screen.getByRole("button", { name: "AI Research" }));

    expect(screen.getByRole("complementary", { name: "Uncava Assistant" })).toBeInTheDocument();
  });

  it("is a complementary region and not a modal dialog", async () => {
    mount();

    await userEvent.click(screen.getByRole("button", { name: "AI Research" }));

    const panel = screen.getByRole("complementary", { name: "Uncava Assistant" });
    // A scrim and aria-modal are what make Drawer right for one record and wrong for this: the grid
    // beside it has to stay clickable and reachable.
    expect(panel).not.toHaveAttribute("aria-modal");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("names the mandate the conversation is about", async () => {
    mount();

    await userEvent.click(screen.getByRole("button", { name: "AI Research" }));

    expect(screen.getByText("Meridian Energy Group · CFO")).toBeInTheDocument();
  });

  it("closes on Escape", async () => {
    mount();
    await userEvent.click(screen.getByRole("button", { name: "AI Research" }));

    await userEvent.keyboard("{Escape}");

    expect(screen.queryByRole("complementary", { name: "Uncava Assistant" })).not.toBeInTheDocument();
  });

  it("puts the caret in the composer when a person opens it", async () => {
    mount();

    await userEvent.click(screen.getByRole("button", { name: "AI Research" }));

    expect(screen.getByRole("textbox", { name: "Ask the assistant" })).toHaveFocus();
  });

  it("does not steal focus when a remembered panel is restored", () => {
    localStorage.setItem("lm.assistant.open", "p1");

    mount();

    // Landing on a screen and having the caret yanked into a panel nobody just opened is its own bug.
    expect(screen.getByRole("complementary", { name: "Uncava Assistant" })).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Ask the assistant" })).not.toHaveFocus();
  });

  it("remembers being open across a remount, because the layouts are siblings", async () => {
    const first = mount();
    await userEvent.click(screen.getByRole("button", { name: "AI Research" }));
    first.unmount();

    mount();

    expect(screen.getByRole("complementary", { name: "Uncava Assistant" })).toBeInTheDocument();
  });

  it("stays shut in another project after being opened in one", async () => {
    const first = mount();
    await userEvent.click(screen.getByRole("button", { name: "AI Research" }));
    first.unmount();

    mount("p2");

    expect(screen.queryByRole("complementary", { name: "Uncava Assistant" })).not.toBeInTheDocument();
  });

  // The panel outlives the close by the length of the collapse, which is the whole reason shutting it
  // no longer snaps. What must not outlive it is any way to reach the thing: hidden and untabbable
  // the moment it starts to go, not when it finishes.
  it("takes the shut panel out of the page while it animates away", async () => {
    mount();
    await userEvent.click(screen.getByRole("button", { name: "AI Research" }));
    const panel = screen.getByRole("complementary", { name: "Uncava Assistant" });

    await userEvent.keyboard("{Escape}");

    expect(panel).toBeInTheDocument();
    expect(panel.closest("[inert]")).toHaveAttribute("aria-hidden", "true");
  });

  it("puts a starter into the composer rather than sending it", async () => {
    mount();
    await userEvent.click(screen.getByRole("button", { name: "AI Research" }));

    await userEvent.click(screen.getByRole("button", { name: /Top 10 retail companies/i }));

    expect(screen.getByRole("textbox", { name: "Ask the assistant" })).toHaveValue(
      "Top 10 retail companies in the United Arab Emirates",
    );
  });
});
