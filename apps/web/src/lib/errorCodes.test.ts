import { describe, expect, it } from "vitest";
import { ApiRequestError } from "./apiClient";
import { codeOf, messageFor } from "./errorCodes";

/** The fallback chain the whole error UX rides on: our copy → server detail → generic line. */
describe("messageFor", () => {
  const failure = (code: string, detail: string) =>
    new ApiRequestError({ code, detail, status: 409, correlationId: "x" });

  it("prefers our copy for a known code", () => {
    expect(messageFor(failure("LAST_ADMIN", "server words"))).toBe(
      "A workspace must keep at least one admin.",
    );
  });

  it("falls back to the server's detail for an unmapped code", () => {
    expect(messageFor(failure("SOMETHING_NEW", "The server explains itself"))).toBe(
      "The server explains itself",
    );
  });

  it("gives a generic line for anything that is not an API failure", () => {
    expect(messageFor(new TypeError("fetch failed"))).toBe("Something went wrong. Try again.");
  });
});

describe("codeOf", () => {
  it("reads the code off an API failure and null off anything else", () => {
    expect(codeOf(new ApiRequestError({ code: "FORBIDDEN", detail: "", status: 403, correlationId: "x" })))
      .toBe("FORBIDDEN");
    expect(codeOf(new Error("boom"))).toBeNull();
  });
});
