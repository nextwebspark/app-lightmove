import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { sampleProgress } from "../../../test/sampleProject";
import { PhaseBar } from "./PhaseBar";

/** The widths are the whole point of the bar, and a caption cannot tell you they are wrong. */
describe("PhaseBar", () => {
  const widthsOf = (container: HTMLElement) =>
    [...container.querySelectorAll<HTMLElement>("span[style]")].map((span) => span.style.width);

  it("fills the whole track for a mapping mandate, which owes no shortlist", () => {
    const { container } = render(
      <PhaseBar progress={sampleProgress({ mapPercent: 65 })} projectType="MAPPING" />,
    );

    expect(widthsOf(container)).toEqual(["65%", "0%"]);
  });

  it("gives a search's map its 60% share, so the engage half has room to show", () => {
    const { container } = render(
      <PhaseBar progress={sampleProgress({ mapPercent: 50 })} projectType="EXECUTIVE_SEARCH" />,
    );

    expect(widthsOf(container)).toEqual(["30%", "0%"]);
  });

  it("draws the engage segment once the map is complete", () => {
    const { container } = render(
      <PhaseBar
        progress={sampleProgress({
          activePhase: "ENGAGE",
          mappingComplete: true,
          mapPercent: 100,
          engagePercent: 30,
        })}
        projectType="EXECUTIVE_SEARCH"
      />,
    );

    // 60% of the track for the finished map, and 30% of the remaining 40% for the engagement.
    expect(widthsOf(container)).toEqual(["60%", "12%"]);
  });
});
