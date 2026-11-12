import { describe, expect, it } from "vitest";
import type { EmailSignature } from "../api/signaturesApi";
import { customBodyFor, draftOf, duplicateOf, newDraft, payloadOf } from "./signatureDraft";

const saved: EmailSignature = {
  id: "s1",
  name: "Formal",
  layout: "PHOTO_LOGO",
  fields: { name: null, title: "Partner", phone: null, linkedinUrl: null, company: null, website: null, address: null, mobile: null },
  showPhoto: true,
  photoShape: "ROUNDED",
  showLogo: true,
  showBookingLink: false,
  customBody: "Festival Tower",
  footerPictures: [{ token: "t1", src: "/api/v1/email-images/t1", width: 96, height: 48, shape: "CIRCLE" }],
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

  it("starts a layout with its own pictures on, and the first one as the default", () => {
    expect(newDraft("PHOTO_LOGO", true, "Yara")).toMatchObject({ name: "Photo + logo", showPhoto: true, showLogo: true, isDefault: true });
    expect(newDraft("CLASSIC", false, "Yara")).toMatchObject({ showPhoto: false, showLogo: false, isDefault: false });
    expect(newDraft("CUSTOM", false, "Yara").customBody).toContain("**Yara**");
  });

  it("sends a cleared field as null, so it follows its source again", () => {
    const draft = draftOf(saved);
    draft.fields.title = "  ";
    draft.fields.phone = " +971 4 000 0000 ";

    const payload = payloadOf(draft);

    expect(payload.fields.title).toBeNull();
    expect(payload.fields.phone).toBe("+971 4 000 0000");
  });

  it("keeps a fixed layout's extra words, footer pictures and photo shape, apart from Custom's words", () => {
    const draft = draftOf(saved);
    expect(draft).toMatchObject({ extras: "Festival Tower", customBody: "", photoShape: "ROUNDED" });

    expect(payloadOf(draft)).toMatchObject({ customBody: "Festival Tower", footerPictures: [{ token: "t1", shape: "CIRCLE" }], photoShape: "ROUNDED" });
    expect(payloadOf({ ...draft, layout: "CUSTOM", customBody: "Mine" }).customBody).toBe("Mine");
  });

  it("names a nameless signature for its layout", () => {
    expect(payloadOf({ ...draftOf(saved), name: "  " }).name).toBe("Photo + logo");
  });

  it("duplicates as a copy that is never the default", () => {
    expect(duplicateOf(saved)).toMatchObject({ name: "Formal (copy)", isDefault: false, fields: { title: "Partner" } });
  });
});
