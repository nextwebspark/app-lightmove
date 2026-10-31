import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { UserAvatar } from "./PopupChrome";

describe("UserAvatar", () => {
  it("shows the account's picture when it has one", () => {
    render(<UserAvatar name="Alok Kumar" src="https://media.example/alok.jpg" />);

    expect(screen.getByRole("img", { name: "Alok Kumar" }).getAttribute("src")).toBe("https://media.example/alok.jpg");
  });

  it("falls back to the initials when there is no picture, or it no longer loads", () => {
    const { rerender } = render(<UserAvatar name="Alok Kumar" src={null} />);
    expect(screen.getByText("AK")).toBeTruthy();

    rerender(<UserAvatar name="Alok Kumar" src="https://media.example/expired.jpg" />);
    fireEvent.error(screen.getByRole("img", { name: "Alok Kumar" }));

    expect(screen.queryByRole("img")).toBeNull();
    expect(screen.getByText("AK")).toBeTruthy();
  });
});
