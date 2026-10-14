import { describe, expect, it } from "vitest";
import { ApiRequestError } from "./apiClient";
import { codeOf, messageFor } from "./errorCodes";

describe("messageFor", () => {
  const failure = (code: string, detail: string, correlationId = "x") =>
    new ApiRequestError({ code, detail, status: 409, correlationId });

  it("prefers our copy for a known code", () => {
    expect(messageFor(failure("LAST_ADMIN", "server words"))).toBe(
      "A workspace must keep at least one admin.",
    );
  });

  it("never shows an unmapped code's detail, and gives the reference to quote instead", () => {
    const message = messageFor(failure("SOMETHING_NEW", "could not execute statement", "req-7f3a"));

    expect(message).toBe(
      "Something went wrong on our side. Try again, or contact support and quote reference req-7f3a.",
    );
    expect(message).not.toContain("statement");
  });

  it("shows the server's sentence for an allow-listed code, since it names the limit", () => {
    const detail = "That file has more than 5,000 rows. Split it and import the parts.";

    expect(messageFor(failure("IMPORT_TOO_MANY_ROWS", detail))).toBe(detail);
  });

  it("names the field a validation refusal named, for a screen with no input to put it under", () => {
    const refused = new ApiRequestError({
      code: "VALIDATION_FAILED",
      detail: "One or more fields are invalid",
      status: 400,
      correlationId: "c1",
      fieldErrors: { "responsibilities[20].text": "size must be between 0 and 200", positionTitle: "Required" },
    });

    expect(messageFor(refused)).toBe("Text: size must be between 0 and 200");
  });

  it("reads a camel-cased field as words", () => {
    const refused = new ApiRequestError({
      code: "VALIDATION_FAILED",
      detail: "One or more fields are invalid",
      status: 400,
      correlationId: "c1",
      fieldErrors: { positionTitle: "Enter a title" },
    });

    expect(messageFor(refused)).toBe("Position title: Enter a title");
  });

  it("drops the reference when the response carried none", () => {
    expect(messageFor(failure("INTERNAL_ERROR", "Something went wrong on our end", "none"))).toBe(
      "Something went wrong. Try again.",
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
