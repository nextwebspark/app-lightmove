import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AvatarStack } from "./AvatarStack";

const people = ["Ada Lovelace", "Grace Hopper", "Alan Turing", "Edsger Dijkstra"].map((name, index) => ({
  id: `p${index}`,
  name,
}));

describe("AvatarStack", () => {
  it("draws the first few and names the rest behind the count", () => {
    render(<AvatarStack people={people} max={2} />);

    expect(screen.getByTitle("Ada Lovelace")).toBeInTheDocument();
    expect(screen.getByTitle("Grace Hopper")).toBeInTheDocument();
    expect(screen.queryByTitle("Alan Turing")).not.toBeInTheDocument();
    expect(screen.getByText("+2")).toHaveAttribute("title", "Alan Turing, Edsger Dijkstra");
  });

  it("draws no count when everyone fits", () => {
    render(<AvatarStack people={people} max={4} />);

    expect(screen.queryByText(/^\+/)).not.toBeInTheDocument();
  });

  it("uses a person's own tooltip when given one", () => {
    render(<AvatarStack people={[{ id: "p0", name: "Ada Lovelace", title: "Ada Lovelace · Lead" }]} max={3} />);

    expect(screen.getByTitle("Ada Lovelace · Lead")).toHaveTextContent("AL");
  });
});
