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

  it("says under the title what the list holds, so it reads as suggestions to file", () => {
    mount({
      proposal: {
        title: "Watch distributors",
        companies: [
          company("a1"),
          company("a2", { stage: "declined" }),
          company("x", { apolloAccountId: null, linkedinSlug: "rivoli" }),
        ],
      },
    });

    expect(screen.getByText("3 suggested · 1 already in this position · 1 from LinkedIn")).toBeInTheDocument();
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

  it("shows a company the mandate already holds with its stage, unticked and not offered for filing", async () => {
    const { onAccept } = mount({
      proposal: {
        title: "Utilities",
        companies: [company("a1"), company("a2", { stage: "declined" })],
      },
    });

    expect(screen.getByText("Declined")).toHaveAttribute("title", "Already in this mandate as Declined");
    const held = screen.getByRole("checkbox", { name: "Company a2 is already Declined" });
    expect(held).not.toBeChecked();
    expect(held).toBeDisabled();

    await userEvent.click(screen.getByRole("button", { name: "Shortlist" }));

    expect(onAccept).toHaveBeenCalledWith(["a1"], "shortlisted");
  });

  it("offers nothing to file when every company is already in the mandate", () => {
    mount({
      proposal: {
        title: "Utilities",
        companies: [company("a1", { stage: "inUniverse" }), company("a2", { stage: "shortlisted" })],
      },
    });

    expect(screen.getByText("All already in this mandate")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Shortlist" })).toBeDisabled();
  });

  it("shows what was filed instead of the buttons once filed", () => {
    mount({ outcome: { status: null, added: 2, skipped: 1 } });

    expect(screen.getByText("2 companies filed to In universe, 1 already in this mandate")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Universe" })).not.toBeInTheDocument();
  });
});
