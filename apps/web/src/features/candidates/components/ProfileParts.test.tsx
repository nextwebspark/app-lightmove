import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { isFittingPill, SHOWN, stubLayout } from "../../../test/stubLayout";
import { PillRow } from "./ProfileParts";

const SKILLS = ["Java", "Scala", "Spark", "Kafka", "Flink", "Beam"];

describe("PillRow", () => {
  afterEach(() => vi.restoreAllMocks());

  it("holds a long list to two lines, with the rest one press away", async () => {
    // Two lines of 100 hold two 48s each; "+N more" takes the second line's last place.
    stubLayout({ rowWidth: 100, widthOf: (element) => (isFittingPill(element) ? 48 : 40) });
    render(<PillRow label="Skills" values={SKILLS} />);

    for (const skill of ["Java", "Scala", "Spark"]) expect(screen.getByText(skill, SHOWN)).toBeInTheDocument();
    for (const skill of ["Kafka", "Flink", "Beam"]) expect(screen.queryByText(skill, SHOWN)).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "+3 more", expanded: false }));
    for (const skill of SKILLS) expect(screen.getByText(skill, SHOWN)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "See less", expanded: true }));
    expect(screen.queryByText("Beam", SHOWN)).not.toBeInTheDocument();
  });

  it("offers nothing to unfold when every value fits", () => {
    stubLayout({ rowWidth: 400, widthOf: (element) => (isFittingPill(element) ? 48 : 40) });
    render(<PillRow label="Languages" values={["English", "Arabic"]} />);

    expect(screen.getByText("Arabic", SHOWN)).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });
});
