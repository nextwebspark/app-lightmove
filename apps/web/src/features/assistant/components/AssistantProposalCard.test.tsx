import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { AssistantProposal, ProposedCompany } from "../api/types";
import { AssistantProposalCard } from "./AssistantProposalCard";

function company(id: string, over: Partial<ProposedCompany> = {}): ProposedCompany {
  return {
    apolloAccountId: id,
    companyName: `Company ${id}`,
    country: "Saudi Arabia",
    employees: 32000,
    logoUrl: null,
    ...over,
  };
}

const PROPOSAL: AssistantProposal = {
  title: "Two utilities",
  companies: [company("a1", { logoUrl: "https://logos.example/a1.png" }), company("a2")],
};

function mount(over: Partial<Parameters<typeof AssistantProposalCard>[0]> = {}) {
  const onAccept = vi.fn();
  render(
    <AssistantProposalCard proposal={PROPOSAL} outcome={null} filing={false} onAccept={onAccept} {...over} />,
  );
  return { onAccept };
}

describe("the assistant's company card", () => {
  it("draws a row per company with its logo, or its initial where there is none", () => {
    mount();

    expect(screen.getByText("Company a1")).toBeInTheDocument();
    expect(screen.getAllByText("Saudi Arabia · 32,000 staff")).toHaveLength(2);
    expect(document.querySelector('img[src="https://logos.example/a1.png"]')).not.toBeNull();
    expect(screen.getByText("C")).toBeInTheDocument();
  });

  it("files only the ticked companies, at the stage pressed", async () => {
    const { onAccept } = mount();

    await userEvent.click(screen.getByRole("checkbox", { name: "Include Company a2" }));
    await userEvent.click(screen.getByRole("button", { name: "Shortlist" }));

    expect(onAccept).toHaveBeenCalledWith(["a1"], "shortlisted");
  });

  it("offers nothing to file when nothing is ticked", async () => {
    mount();

    await userEvent.click(screen.getByRole("checkbox", { name: "Include Company a1" }));
    await userEvent.click(screen.getByRole("checkbox", { name: "Include Company a2" }));

    expect(screen.getByText("Nothing selected")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Universe" })).toBeDisabled();
  });

  it("files a company researched on LinkedIn by its slug, and names the brand a partner runs", async () => {
    const { onAccept } = mount({
      proposal: {
        title: "Global retailers",
        companies: [
          company("a1", { companyName: "Majid Al Futtaim", operates: "Carrefour" }),
          { ...company("x"), apolloAccountId: null, linkedinSlug: "ikea", companyName: "IKEA" },
        ],
      },
    });

    expect(screen.getByText("Operates Carrefour")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "LinkedIn" })).toHaveAttribute("href", "https://www.linkedin.com/company/ikea");

    await userEvent.click(screen.getByRole("button", { name: "Shortlist" }));

    expect(onAccept).toHaveBeenCalledWith(["a1", "ikea"], "shortlisted");
  });

  it("shows what was filed instead of the buttons once filed", () => {
    mount({ outcome: { status: null, added: 2, skipped: 1 } });

    expect(screen.getByText("2 companies filed to In universe, 1 already in this mandate")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Universe" })).not.toBeInTheDocument();
  });
});
