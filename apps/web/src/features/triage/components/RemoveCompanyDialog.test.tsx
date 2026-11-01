import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { TriageCompany } from "../api/types";
import { RemoveCompanyDialog } from "./RemoveCompanyDialog";

const acwa = { id: "u1", companyName: "ACWA Power" } as TriageCompany;

function renderDialog(executiveCount: number | undefined, onConfirm = vi.fn()) {
  render(
    <RemoveCompanyDialog
      company={acwa}
      executiveCount={executiveCount}
      removing={false}
      onCancel={vi.fn()}
      onConfirm={onConfirm}
    />,
  );
  return onConfirm;
}

describe("RemoveCompanyDialog", () => {
  it("warns nothing about people when nobody is mapped at the company", () => {
    renderDialog(0);

    expect(screen.queryByText(/executive/)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Remove from mandate" })).toBeEnabled();
  });

  it("says the one executive leaves the position with the company", () => {
    renderDialog(1);

    expect(screen.getByText(/ACWA Power has 1 executive\. Removing the company removes them from this position too\./))
      .toBeInTheDocument();
  });

  it("says every executive leaves with it, and that they stay on the Candidates page", async () => {
    const onConfirm = renderDialog(3);

    expect(screen.getByText(/ACWA Power has 3 executives\. Removing the company removes all of them/))
      .toBeInTheDocument();
    expect(screen.getByText(/They stay on the Candidates page/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Remove from mandate" }));
    expect(onConfirm).toHaveBeenCalledWith(acwa);
  });

  it("waits for the count before it lets the removal go", () => {
    renderDialog(undefined);

    expect(screen.getByRole("button", { name: "Remove from mandate" })).toBeDisabled();
  });
});
