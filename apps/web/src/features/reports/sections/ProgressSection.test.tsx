import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { SAMPLE_REPORT } from "../../../test/sampleReport";
import type { ReportProgress } from "../api/types";
import { ProgressSection } from "./ProgressSection";

/**
 * The chapter's honesty about what it cannot yet measure. A projection the rows do not support is
 * worse than none: a mandate a day old used to be shown a pace of 0.0/wk in the risk tone and a
 * completion date equal to its own kickoff.
 */
describe("ProgressSection", () => {
  function show(progress: Partial<ReportProgress>) {
    render(<ProgressSection progress={{ ...SAMPLE_REPORT.progress, ...progress }} />);
  }

  const basisToggle = () => screen.queryByRole("radiogroup", { name: "Projection basis" });

  it("projects, and offers the basis, on a mandate with history", () => {
    show({});

    expect(basisToggle()).toBeInTheDocument();
    expect(screen.getByText(/projected 27 Sep/)).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Are we going to hit the deadline?" })).toBeInTheDocument();
  });

  it("states that nothing is scoped rather than reporting a covered universe of none", () => {
    show({ asOf: "2026-07-21", targetCompanies: 0, companiesCumulative: [0], daysSinceLastExecutive: null });

    expect(screen.getByText("No companies scoped to this mandate yet.")).toBeInTheDocument();
    expect(screen.queryByText(/coverage is complete/)).not.toBeInTheDocument();
    expect(screen.queryByText("Companies mapped")).not.toBeInTheDocument();
    expect(basisToggle()).not.toBeInTheDocument();
  });

  it("names the missing history instead of printing a pace of zero", () => {
    show({ asOf: "2026-07-22", companiesCumulative: [2], daysSinceLastExecutive: 0 });

    expect(screen.getByText("Coverage so far")).toBeInTheDocument();
    expect(screen.getByText(/Nothing to project yet/)).toBeInTheDocument();
    expect(screen.getByText("needs a full week of history")).toBeInTheDocument();
    expect(screen.queryByText("0.0")).not.toBeInTheDocument();
    expect(screen.queryByText("Projected")).not.toBeInTheDocument();
    expect(screen.queryByText(/– projected/)).not.toBeInTheDocument();
    expect(basisToggle()).not.toBeInTheDocument();
  });

  it("calls a covered universe covered, without naming a date for it", () => {
    show({ asOf: "2026-07-21", targetCompanies: 2, companiesCumulative: [2], daysSinceLastExecutive: 0 });

    expect(screen.getByText(/coverage is complete/)).toBeInTheDocument();
    expect(screen.getByText("cumulative companies mapped · from 21 Jul")).toBeInTheDocument();
    expect(screen.queryByText(/– projected/)).not.toBeInTheDocument();
    expect(basisToggle()).not.toBeInTheDocument();
  });

  it("compares the first month with recently only once the two spans stop overlapping", () => {
    // Four complete weeks: "the first four" and "the last three" are largely the same weeks, and at
    // one week they were the same week — the sentence read "from ~8/week … to ~8/week".
    show({
      asOf: "2026-08-17",
      companiesCumulative: [0, 5, 11, 17],
      weekly: SAMPLE_REPORT.progress.weekly.slice(0, 4),
    });

    expect(screen.queryByText(/Weekly pace has gone from/)).not.toBeInTheDocument();
  });

  it("states the comparison once seven complete weeks stand behind it", () => {
    show({});

    expect(screen.getByText(/Weekly pace has gone from/)).toBeInTheDocument();
  });

  it("names the basis the pace tile is actually showing", async () => {
    const user = userEvent.setup();
    show({});

    expect(screen.getByText("Recent pace")).toBeInTheDocument();
    await user.click(screen.getByRole("radio", { name: "Full-mandate avg" }));

    // The figure follows the toggle; the label used to say "Recent" over the full-mandate average.
    expect(screen.getByText("Full-mandate pace")).toBeInTheDocument();
    expect(screen.queryByText("Recent pace")).not.toBeInTheDocument();
  });

  it("keys the week in progress in the momentum legend, since it is drawn a third colour", () => {
    show({});

    expect(screen.getByText("Week in progress")).toBeInTheDocument();
  });

  it("keeps the basis toggle on a stall, where the other basis is the comparison worth making", () => {
    show({ companiesCumulative: [0, 10, 10, 10, 10, 10, 10, 10] });

    expect(screen.getByText(/no pace to project from/)).toBeInTheDocument();
    expect(basisToggle()).toBeInTheDocument();
  });
});
