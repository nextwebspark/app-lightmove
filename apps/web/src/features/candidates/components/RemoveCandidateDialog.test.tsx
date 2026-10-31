import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { Candidate } from "../api/types";
import { RemoveCandidateDialog } from "./RemoveCandidateDialog";

const yasmin = {
  id: "c1",
  fullName: "Yasmin El-Sayed",
  triageCompanyId: "u1",
  companyName: "ACWA Power",
} as Candidate;

function renderDialog(candidate: Candidate, soleAtCompany: boolean, onConfirm = vi.fn()) {
  render(
    <RemoveCandidateDialog
      candidate={candidate}
      soleAtCompany={soleAtCompany}
      removing={false}
      onCancel={vi.fn()}
      onConfirm={onConfirm}
    />,
  );
  return onConfirm;
}

describe("RemoveCandidateDialog", () => {
  it("removes the person alone unless the company box is ticked", async () => {
    const onConfirm = renderDialog(yasmin, true);

    expect(screen.getByRole("checkbox", { name: /Also remove ACWA Power — no one else is at it/ })).not.toBeChecked();
    await userEvent.click(screen.getByRole("button", { name: "Remove from mandate" }));

    expect(onConfirm).toHaveBeenCalledWith(yasmin, { withCompany: false });
  });

  it("takes the company too when the box is ticked", async () => {
    const onConfirm = renderDialog(yasmin, true);

    await userEvent.click(screen.getByRole("checkbox", { name: /Also remove ACWA Power/ }));
    await userEvent.click(screen.getByRole("button", { name: "Remove from mandate" }));

    expect(onConfirm).toHaveBeenCalledWith(yasmin, { withCompany: true });
  });

  it("offers no company while someone else is at it", () => {
    renderDialog(yasmin, false);

    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  });

  it("offers no company for someone at none", () => {
    renderDialog({ ...yasmin, triageCompanyId: null, companyName: "Outside Holdings" }, true);

    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  });
});
