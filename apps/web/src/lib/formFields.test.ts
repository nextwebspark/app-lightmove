import { describe, expect, it } from "vitest";
import { optionalNumber } from "./formFields";

/** Shared by the company facts form (Employees, Revenue) and the candidate drawer's figures. */
describe("optionalNumber", () => {
  const employees = optionalNumber("Employees", 10_000_000);

  const messageFor = (input: string) => {
    const result = employees.safeParse(input);
    return result.success ? null : result.error.issues[0].message;
  };

  it("treats an untouched field as omitted rather than as zero", () => {
    expect(employees.parse("")).toBeUndefined();
    expect(employees.parse("   ")).toBeUndefined();
  });

  it("accepts a number, zero included", () => {
    expect(employees.parse("4200")).toBe(4200);
    expect(employees.parse("0")).toBe(0);
  });

  // A negative used to be caught by the same refine that catches "abc", so entering -500 was told
  // to enter a number — the one thing the user had already done.
  it("tells a negative to be 0 or greater, not to be a number", () => {
    expect(messageFor("-500")).toBe("Employees must be 0 or greater");
  });

  it("still says 'must be a number' for something that is not one", () => {
    expect(messageFor("many")).toBe("Employees must be a number");
  });

  it("refuses a figure past the field's ceiling", () => {
    expect(messageFor("20000000")).toBe("That employees looks like a typo");
  });
});
