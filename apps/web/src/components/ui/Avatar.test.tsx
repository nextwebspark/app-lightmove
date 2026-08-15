import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Avatar } from "./Avatar";

/**
 * What we hold is the identity provider's CDN URL, not a copy of the image, and LinkedIn's expire
 * within weeks. So a picture that fails to load is an ordinary state rather than an error, and the
 * fallback is the behaviour worth pinning: a torn-image icon on the roster would be worse than the
 * initials this product used before pictures existed.
 */
describe("Avatar", () => {
  it("shows initials when the person has no picture", () => {
    render(<Avatar id="u1" name="Sara Al-Mansour" />);

    expect(screen.getByTitle("Sara Al-Mansour")).toHaveTextContent("SA");
  });

  it("shows the picture when there is one", () => {
    render(<Avatar id="u1" name="Sara Al-Mansour" src="https://media.licdn.com/sara.jpg" />);

    expect(screen.getByTitle("Sara Al-Mansour")).toHaveAttribute(
      "src",
      "https://media.licdn.com/sara.jpg",
    );
  });

  it("falls back to initials when the picture fails to load", () => {
    render(<Avatar id="u1" name="Sara Al-Mansour" src="https://media.licdn.com/expired.jpg" />);

    fireEvent.error(screen.getByTitle("Sara Al-Mansour"));

    expect(screen.getByTitle("Sara Al-Mansour")).toHaveTextContent("SA");
  });
});
