import { describe, expect, it } from "vitest";
import { ApiRequestError } from "./apiClient";
import { fieldErrorsFrom } from "./formErrors";

const refusal = (code: string, detail: string, fieldErrors?: Record<string, string>) =>
  new ApiRequestError({ code, detail, status: 400, correlationId: "c1", fieldErrors });

const NAMES = { positionTitle: "positionTitle", customName: "newClientName" } as const;

describe("fieldErrorsFrom", () => {
  it("attributes a mapped field and leaves the banner empty", () => {
    const result = fieldErrorsFrom(
      refusal("VALIDATION_FAILED", "One or more fields are invalid", {
        positionTitle: "That title is too long",
      }),
      NAMES,
    );

    expect(result.fields).toEqual({ positionTitle: "That title is too long" });
    expect(result.formMessage).toBeNull();
  });

  // Its own words beat "One or more fields are invalid", which points at nothing.
  it("puts an unmapped field's message in the banner", () => {
    const result = fieldErrorsFrom(
      refusal("VALIDATION_FAILED", "One or more fields are invalid", {
        clientId: "Choose a client",
      }),
      NAMES,
    );

    expect(result.fields).toEqual({});
    expect(result.formMessage).toBe("Choose a client");
  });

  // The mixed case: one mapped key used to return early and discard the rest, so the user fixed the
  // half they were shown and was refused again for a reason nothing had named.
  it("keeps the banner for an unmapped field even when another was attributed", () => {
    const result = fieldErrorsFrom(
      refusal("VALIDATION_FAILED", "One or more fields are invalid", {
        positionTitle: "That title is too long",
        clientId: "Choose a client",
      }),
      NAMES,
    );

    expect(result.fields).toEqual({ positionTitle: "That title is too long" });
    expect(result.formMessage).toBe("Choose a client");
  });

  // One entry answers every row, so a form posting many rows needs no entry per index.
  it("matches a row-indexed key on its index-free name", () => {
    const result = fieldErrorsFrom(
      refusal("VALIDATION_FAILED", "One or more fields are invalid", {
        "requests[7].email": "That doesn't look like a valid email",
      }),
      { "requests.email": "email" },
    );

    expect(result.fields).toEqual({ email: "That doesn't look like a valid email" });
    expect(result.formMessage).toBeNull();
  });

  it("prefers an exact key over the index-free one", () => {
    const result = fieldErrorsFrom(
      refusal("VALIDATION_FAILED", "One or more fields are invalid", {
        "requests[0].email": "Enter an email address",
      }),
      { "requests[0].email": "firstRow", "requests.email": "anyRow" },
    );

    expect(result.fields).toEqual({ firstRow: "Enter an email address" });
  });

  it("sends a failure carrying no field errors to the banner", () => {
    const result = fieldErrorsFrom(refusal("FORBIDDEN", "nope"), NAMES);

    expect(result.fields).toEqual({});
    expect(result.formMessage).toBe("You don't have permission to do this.");
  });

  it("falls back to the generic line for something that is not an API failure", () => {
    const result = fieldErrorsFrom(new TypeError("Failed to fetch"), NAMES);

    expect(result.fields).toEqual({});
    expect(result.formMessage).toBe("Something went wrong. Try again.");
  });
});
