import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { NoteText } from "./NoteText";

describe("a note's words", () => {
  it("draws a formatted note's marks, lists, checklist and mentions", async () => {
    render(
      <NoteText
        format="markdown"
        body={"## Call\n\n**Wants** ++Dubai++ @[Omar Khalil](user:abc-123)\n\n- [x] Brief sent\n- [ ] CV"}
      />,
    );

    expect(await screen.findByRole("heading", { level: 2, name: "Call" })).toBeInTheDocument();
    expect(screen.getByText("Wants").tagName).toBe("STRONG");
    expect(screen.getByText("Dubai").tagName).toBe("U");
    expect(screen.getByText("@Omar Khalil")).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: "Done" })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: "Not done" })).not.toBeChecked();
  });

  it("opens a safe link in a new tab and leaves an unsafe one as words", async () => {
    render(<NoteText format="markdown" body={"[site](https://example.com) and [bad](javascript:alert(1))"} />);

    const link = await screen.findByRole("link", { name: "site" });
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
    expect(screen.queryByRole("link", { name: "bad" })).not.toBeInTheDocument();
  });

  it("draws a plain note as typed, its markup characters untouched", () => {
    render(<NoteText format="plain" body={"**not bold**\n1. not a list"} />);

    expect(screen.getByText(/\*\*not bold\*\*/)).toBeInTheDocument();
  });
});
