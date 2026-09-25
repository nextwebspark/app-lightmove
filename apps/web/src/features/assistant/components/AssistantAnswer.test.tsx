import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AssistantAnswer } from "./AssistantAnswer";

describe("an assistant answer", () => {
  it("draws a Markdown list as a list, with bold names", () => {
    render(<AssistantAnswer text={"Also consider:\n\n* **Food & Beverage**, retail presence.\n* **Technology**, e-commerce."} />);

    expect(screen.getAllByRole("listitem")).toHaveLength(2);
    expect(screen.getByText("Food & Beverage").tagName).toBe("STRONG");
    expect(screen.queryByText(/\*\*/)).not.toBeInTheDocument();
  });

  it("keeps separate paragraphs apart", () => {
    const { container } = render(<AssistantAnswer text={"First line.\n\nSecond line."} />);

    expect(container.querySelectorAll("p")).toHaveLength(2);
  });

  it("never renders HTML written into the answer", () => {
    const { container } = render(<AssistantAnswer text={"Hello <script>alert(1)</script> <b>bold</b>"} />);

    expect(container.querySelector("script")).toBeNull();
    expect(container.querySelector("b")).toBeNull();
  });

  it("draws a heading as plain text rather than a heading", () => {
    render(<AssistantAnswer text={"# Sectors"} />);

    expect(screen.queryByRole("heading")).not.toBeInTheDocument();
    expect(screen.getByText("Sectors")).toBeInTheDocument();
  });
});
