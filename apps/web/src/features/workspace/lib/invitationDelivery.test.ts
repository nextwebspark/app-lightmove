import { describe, expect, it } from "vitest";
import { undeliveredInvitationsMessage } from "./invitationDelivery";

describe("undeliveredInvitationsMessage", () => {
  it("names one address, and counts several", () => {
    expect(undeliveredInvitationsMessage(["sara@firm.com"])).toBe(
      "The invitation to sara@firm.com is saved, but the email didn't go out. Resend it from Team.",
    );
    expect(undeliveredInvitationsMessage(["a@firm.com", "b@firm.com"])).toMatch(/^The invitation to 2 people is saved/);
  });
});
