import type { EmailSignature, SaveSignaturePayload, SignatureFields, SignatureLayout } from "../api/signaturesApi";

/**
 * What a new signature starts from. Each is only a starting point: the server fills it in from the profile, the
 * workspace and the mailbox, and from then on it is the person's own words in the editor.
 */
export const STARTERS = [
  { layout: "LOGO_BANNER", label: "With logo", hint: "Your details, then your company logo" },
  { layout: "PHOTO_LOGO", label: "With photo", hint: "Your photo, your details and your company logo" },
  { layout: "CUSTOM", label: "Custom", hint: "Start from your name and write the rest" },
] as const satisfies readonly { layout: SignatureLayout; label: string; hint: string }[];

export type StarterLayout = (typeof STARTERS)[number]["layout"];

/** What the editor holds: every signature is written in the editor, whatever it started from. */
export interface SignatureDraft {
  name: string;
  customBody: string;
  showBookingLink: boolean;
  disclaimer: string;
  isDefault: boolean;
}

const NO_FIELDS: SignatureFields = {
  name: null,
  title: null,
  phone: null,
  linkedinUrl: null,
  company: null,
  website: null,
  address: null,
};

/**
 * A new signature from a starting point: a Custom one opens on the person's name; the others are drawn by the server
 * first (`starterRequestOf`), so the editor opens on them already filled in.
 */
export function newDraft(layout: StarterLayout, isFirst: boolean, ownName: string): SignatureDraft {
  return {
    name: STARTERS.find((starter) => starter.layout === layout)?.label ?? "Signature",
    customBody: layout === "CUSTOM" ? customBodyFor(ownName) : "",
    showBookingLink: false,
    disclaimer: "",
    isDefault: isFirst,
  };
}

/** The request the server turns into the editor's Markdown: a starting point's pictures on. */
export function starterRequestOf(layout: Exclude<StarterLayout, "CUSTOM">, name: string): SaveSignaturePayload {
  return {
    name,
    layout,
    fields: { ...NO_FIELDS },
    showPhoto: true,
    showLogo: true,
    showBookingLink: false,
    customBody: null,
    disclaimer: null,
    isDefault: false,
  };
}

/** What a Custom signature starts from, so the editor never opens empty. */
export function customBodyFor(name: string): string {
  // Trimmed: CommonMark reads `**Alok Kumar **` as literal asterisks, and a profile name may end in a space.
  const signedAs = name.trim();
  return signedAs ? `Kind regards,\n\n**${signedAs}**` : "Kind regards,";
}

/**
 * A signature as the editor opens it. One saved before every signature was written in the editor — a fixed layout, or
 * a Custom one with the logo switch on — is drawn as Markdown by the server first (`needsConversion`).
 */
export function draftOf(signature: EmailSignature): SignatureDraft {
  return {
    name: signature.name,
    customBody: signature.customBody ?? "",
    showBookingLink: signature.showBookingLink,
    disclaimer: signature.disclaimer ?? "",
    isDefault: signature.isDefault,
  };
}

export function needsConversion(signature: EmailSignature): boolean {
  return signature.layout !== "CUSTOM" || signature.showLogo;
}

/** The signature as saved, for the server to turn into Markdown. */
export function savedRequestOf(signature: EmailSignature): SaveSignaturePayload {
  const { name, layout, fields, showPhoto, showLogo, showBookingLink, customBody, disclaimer, isDefault } = signature;
  return { name, layout, fields, showPhoto, showLogo, showBookingLink, customBody, disclaimer, isDefault };
}

/** The request: always a Custom signature, its pictures in its words. */
export function payloadOf(draft: SignatureDraft): SaveSignaturePayload {
  return {
    name: draft.name.trim() || "Signature",
    layout: "CUSTOM",
    fields: { ...NO_FIELDS },
    showPhoto: false,
    showLogo: false,
    showBookingLink: draft.showBookingLink,
    customBody: blankAsNull(draft.customBody),
    disclaimer: blankAsNull(draft.disclaimer),
    isDefault: draft.isDefault,
  };
}

/** A copy for Duplicate: never the default, and named so the two can be told apart. */
export function duplicateOf(signature: EmailSignature): SaveSignaturePayload {
  return { ...savedRequestOf(signature), name: `${signature.name} (copy)`.slice(0, 60), isDefault: false };
}

function blankAsNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed === "" ? null : trimmed;
}
