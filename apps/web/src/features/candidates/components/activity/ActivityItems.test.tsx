import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { PreviewOf } from "./ActivityItems";

const note = { type: "note", noteKind: "CALL", aboutThePerson: false, edited: null } as const;

describe("PreviewOf", () => {
  it("draws a call logged by its outcome alone as live, never as deleted", () => {
    render(<PreviewOf preview={{ ...note, excerpt: "", outcome: "no_answer" }} />);
    expect(screen.getByText("No answer")).toBeInTheDocument();
    expect(screen.queryByText("This note was deleted.")).not.toBeInTheDocument();
  });

  it("says a note was deleted once its excerpt is gone", () => {
    render(<PreviewOf preview={{ ...note, excerpt: null, outcome: null }} />);
    expect(screen.getByText("This note was deleted.")).toBeInTheDocument();
  });
});
