import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { AssistantProposal, ProposedCompany } from "../api/types";
import { AssistantProposalCard } from "./AssistantProposalCard";

/**
 * The card a consultant files a market from.
 *
 * <p>The assertions worth having are the ones about what leaves it: which refs a press carries and
 * which stage it names. A row drawn wrongly is a cosmetic bug; a row filed that nobody ticked is a
 * company in a client's mandate that nobody chose.
 */
function company(over: Partial<ProposedCompany> & { ref: string }): ProposedCompany {
  return {
    origin: "UNIVERSE",
    apolloAccountId: `apollo-${over.ref}`,
    companyName: `Company ${over.ref}`,
    country: "Saudi Arabia",
    employees: 32000,
    ...over,
  };
}

function proposal(over: Partial<AssistantProposal> = {}): AssistantProposal {
  return {
    projectId: "p1",
    title: "2 companies not yet in your universe",
    companies: [company({ ref: "c1" }), company({ ref: "c2" })],
    accepted: null,
    ...over,
  };
}

function mount(over: Partial<Parameters<typeof AssistantProposalCard>[0]> = {}) {
  const onAccept = vi.fn();
  const onDismiss = vi.fn();
  render(
    <AssistantProposalCard
      proposal={proposal()}
      running={false}
      filing={false}
      onAccept={onAccept}
      onDismiss={onDismiss}
      {...over}
    />,
  );
  return { onAccept, onDismiss };
}

describe("the assistant's proposal card", () => {
  it("draws a row per company, with where it stands", () => {
    mount();

    expect(screen.getByText("Company c1")).toBeInTheDocument();
    expect(screen.getAllByText("Saudi Arabia · 32,000 staff")).toHaveLength(2);
    expect(screen.getAllByTitle(/Already in your universe/)).toHaveLength(2);
  });

  it("says unknown rather than inventing a headcount it was not given", () => {
    mount({ proposal: proposal({ companies: [company({ ref: "c1", employees: null })] }) });

    expect(screen.getByText("Saudi Arabia · unknown")).toBeInTheDocument();
  });

  // A resolved row is ours and starts in; an unverified one has to be somebody's decision rather
  // than something they failed to undo. Every row is UNIVERSE today, so this is what #464 inherits.
  it("starts a resolved row ticked and an unverified one not", () => {
    mount({
      proposal: proposal({
        companies: [company({ ref: "c1" }), company({ ref: "c2", origin: "WEB" })],
      }),
    });

    expect(screen.getByRole("checkbox", { name: "Include Company c1" })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: "Include Company c2" })).not.toBeChecked();
  });

  it("files only what is ticked, at the stage that was pressed", async () => {
    const { onAccept } = mount();

    await userEvent.click(screen.getByRole("checkbox", { name: "Include Company c1" }));
    await userEvent.click(screen.getByRole("button", { name: "Shortlist" }));

    expect(onAccept).toHaveBeenCalledWith(["c2"], "shortlisted");
  });

  it("names the other two stages correctly too", async () => {
    const { onAccept } = mount();

    await userEvent.click(screen.getByRole("button", { name: "Universe" }));
    await userEvent.click(screen.getByRole("button", { name: "Decline" }));

    expect(onAccept).toHaveBeenNthCalledWith(1, ["c1", "c2"], "inUniverse");
    expect(onAccept).toHaveBeenNthCalledWith(2, ["c1", "c2"], "declined");
  });

  it("offers nothing to press while the turn is still answering", () => {
    mount({ running: true });

    expect(screen.getByRole("button", { name: "Universe" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Shortlist" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Decline" })).toBeDisabled();
    // The server refuses an accept against a RUNNING turn, so saying why beats a dead button.
    expect(screen.getByText("Waiting for the answer to finish")).toBeInTheDocument();
  });

  it("will not file an empty selection", async () => {
    mount({ proposal: proposal({ companies: [company({ ref: "c1" })] }) });

    await userEvent.click(screen.getByRole("checkbox", { name: "Include Company c1" }));

    expect(screen.getByText("Nothing selected")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Universe" })).toBeDisabled();
  });

  // The refresh case: a card already filed must never offer its buttons again, whoever reopens it.
  it("reads back as its outcome once it has been filed", () => {
    mount({
      proposal: proposal({
        accepted: { status: "shortlisted", refs: ["c1"], added: 1, skipped: 1 },
      }),
    });

    expect(screen.getByText("1 company filed to Shortlisted, 1 already in this mandate")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Shortlist" })).not.toBeInTheDocument();
  });

  it("reads an accept that named no stage as the universe, which is what the server did", () => {
    mount({
      proposal: proposal({ accepted: { status: "", refs: ["c1", "c2"], added: 2, skipped: 0 } }),
    });

    expect(screen.getByText("2 companies filed to In universe")).toBeInTheDocument();
  });

  it("dismisses without filing anything", async () => {
    const { onAccept, onDismiss } = mount();

    await userEvent.click(screen.getByRole("button", { name: "Dismiss" }));

    expect(onDismiss).toHaveBeenCalledOnce();
    expect(onAccept).not.toHaveBeenCalled();
  });

  it("counts what the card is made of", () => {
    mount({
      proposal: proposal({
        companies: [
          company({ ref: "c1" }),
          company({ ref: "c2", origin: "RESEARCHED" }),
          company({ ref: "c3", origin: "WEB" }),
        ],
      }),
    });

    expect(screen.getByText("1 already held · 1 verified · 1 from the web")).toBeInTheDocument();
  });
});
