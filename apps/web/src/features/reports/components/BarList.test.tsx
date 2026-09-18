import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { BarList } from "./BarList";

/** The accent says "largest", so it follows the count rather than the order the rows arrive in. */
describe("BarList", () => {
  it("accents the widest row even when a folded tail puts it last", () => {
    render(
      <BarList
        rows={[
          { key: "retail", label: "Retail", count: 12, title: "retail" },
          { key: "farming", label: "Farming", count: 7, title: "farming" },
          { key: "Other", label: "Other", count: 30, title: "other" },
        ]}
      />,
    );

    const barOf = (title: string) => screen.getByTitle(title).querySelector("span > span");
    expect(barOf("other")).toHaveClass("bg-u-accent");
    expect(barOf("retail")).not.toHaveClass("bg-u-accent");
    expect(barOf("other")).toHaveStyle({ width: "100%" });
  });
});
