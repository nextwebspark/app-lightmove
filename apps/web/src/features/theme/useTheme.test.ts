import { afterEach, describe, expect, it } from "vitest";
import { applyStoredTheme } from "./useTheme";

/** UNCAVA is dark-first: an unset preference opens dark, whatever the operating system says. */
describe("applyStoredTheme", () => {
  afterEach(() => {
    localStorage.clear();
    document.body.classList.remove("dark");
  });

  it("opens dark when nobody has chosen", () => {
    applyStoredTheme();
    expect(document.body).toHaveClass("dark");
  });

  it("keeps light once it has been chosen", () => {
    localStorage.setItem("lm-theme", "light");
    applyStoredTheme();
    expect(document.body).not.toHaveClass("dark");
  });
});
