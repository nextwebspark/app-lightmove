import { describe, expect, it } from "vitest";
import type { EmailSignature } from "../api/signaturesApi";
import { customBodyFor, draftOf, duplicateOf, needsConversion, newDraft, payloadOf, starterRequestOf } from "./signatureDraft";

const saved: EmailSignature = {
  id: "s1",
  name: "Formal",
  layout: "PHOTO_LOGO",
  fields: { name: null, title: "Partner", phone: null, linkedinUrl: null, company: null, website: null, address: null },
  showPhoto: true,
  showLogo: true,
  showBookingLink: false,
  customBody: null,
  disclaimer: null,
  isDefault: true,
  updatedAt: "2026-11-01T10:00:00Z",
  html: "<div></div>",
};

describe("signature drafts", () => {
  it("signs a Custom body in bold whatever spaces the profile name carries", () => {
    expect(customBodyFor("Alok Kumar ")).toBe("Kind regards,\n\n**Alok Kumar**");
    expect(customBodyFor("  ")).toBe("Kind regards,");
  });

  it("names a new one for its starting point, the first one the default", () => {
    expect(newDraft("PHOTO_LOGO", true, "Yara")).toMatchObject({ name: "With photo", customBody: "", isDefault: true });
    expect(newDraft("CUSTOM", false, "Yara")).toMatchObject({ name: "Custom", isDefault: false });
    expect(newDraft("CUSTOM", false, "Yara").customBody).toContain("**Yara**");
    expect(starterRequestOf("LOGO_BANNER", "With logo")).toMatchObject({ layout: "LOGO_BANNER", showPhoto: true, showLogo: true });
  });

  it("saves every signature as Custom, its pictures in its words", () => {
    const payload = payloadOf({ ...draftOf(saved), customBody: " **Yara** ", name: "  " });

    expect(payload).toMatchObject({ layout: "CUSTOM", showPhoto: false, showLogo: false, customBody: "**Yara**", name: "Signature" });
    expect(Object.values(payload.fields).every((value) => value === null)).toBe(true);
  });

  it("opens a fixed layout, or a Custom one with the logo switch on, through the server", () => {
    expect(needsConversion(saved)).toBe(true);
    expect(needsConversion({ ...saved, layout: "CUSTOM", showLogo: true })).toBe(true);
    expect(needsConversion({ ...saved, layout: "CUSTOM", showLogo: false })).toBe(false);
  });

  it("duplicates as a copy that is never the default", () => {
    expect(duplicateOf(saved)).toMatchObject({ name: "Formal (copy)", isDefault: false, fields: { title: "Partner" } });
  });
});
