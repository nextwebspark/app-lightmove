import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { Criterion } from "../api/types";
import { BRIEF_LIMITS } from "../lib/briefLimits";
import { CriteriaList } from "./CriteriaList";

const criterion = (text: string): Criterion => ({ text, mode: "REQUIRED", source: "MANUAL" });

describe("CriteriaList", () => {
  it("does not add a criterion the list already holds, whatever its case", async () => {
    const onChange = vi.fn();
    render(<CriteriaList criteria={[criterion("Board experience")]} onChange={onChange} />);

    await userEvent.type(screen.getByLabelText("Add a criterion"), "board EXPERIENCE{Enter}");

    expect(onChange).not.toHaveBeenCalled();
  });

  it("stops offering to add once the server's limit is reached", () => {
    const full = Array.from({ length: BRIEF_LIMITS.criteria }, (_, index) => criterion(`Criterion ${index}`));
    render(<CriteriaList criteria={full} onChange={vi.fn()} />);

    expect(screen.getByLabelText("Add a criterion")).toBeDisabled();
    expect(screen.getByPlaceholderText(`At most ${BRIEF_LIMITS.criteria} criteria`)).toBeInTheDocument();
  });

  it("caps a criterion at the length the server takes", () => {
    render(<CriteriaList criteria={[criterion("Board experience")]} onChange={vi.fn()} />);

    expect(screen.getByLabelText("Criterion 1")).toHaveAttribute("maxLength", String(BRIEF_LIMITS.criterion));
  });
});
