import { describe, expect, it } from "vitest";
import { passwordChangeSchema } from "./passwordChangeSchema";

const values = (overrides: Partial<Record<string, string>> = {}) => ({
  currentPassword: "oldpassword1",
  newPassword: "brandnew42",
  confirmPassword: "brandnew42",
  ...overrides,
});

const errorOn = (field: string, input: Record<string, string>) => {
  const result = passwordChangeSchema.safeParse(input);
  expect(result.success).toBe(false);
  return result.success ? "" : (result.error.issues.find((i) => i.path[0] === field)?.message ?? "");
};

/** The rules the server also enforces — the client states them so the user hears them instantly. */
describe("passwordChangeSchema", () => {
  it("accepts a change that meets every rule", () => {
    expect(passwordChangeSchema.safeParse(values()).success).toBe(true);
  });

  it("asks for the current password before anything else", () => {
    expect(errorOn("currentPassword", values({ currentPassword: "" }))).toBe(
      "Enter your current password",
    );
  });

  it("holds the new password to the same rule as signup", () => {
    expect(errorOn("newPassword", values({ newPassword: "short1", confirmPassword: "short1" })))
      .toBe("Use at least 8 characters");
    expect(errorOn("newPassword", values({ newPassword: "nodigitshere", confirmPassword: "nodigitshere" })))
      .toBe("Include at least one number");
  });

  it("measures the BCrypt ceiling in bytes, not characters", () => {
    // 41 × "é" is 41 characters and 83 bytes — under any character count, over BCrypt's 72-byte limit.
    const accented = "é".repeat(41) + "1";
    expect(errorOn("newPassword", values({ newPassword: accented, confirmPassword: accented })))
      .toContain("at most 72 characters");
  });

  it("refuses a mismatched confirmation", () => {
    expect(errorOn("confirmPassword", values({ confirmPassword: "somethingelse1" }))).toBe(
      "Those passwords don't match",
    );
  });

  it("refuses a new password that is the current one", () => {
    expect(
      errorOn("newPassword", values({ newPassword: "oldpassword1", confirmPassword: "oldpassword1" })),
    ).toBe("Choose a password different from your current one");
  });
});
