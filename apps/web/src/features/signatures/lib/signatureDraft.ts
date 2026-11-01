import type { EmailSignature, SaveSignaturePayload, SignatureFields, SignatureLayout } from "../api/signaturesApi";

/** Each layout as the gallery and the editor's chips name it. */
export const LAYOUT_DETAILS: Record<SignatureLayout, { label: string; hint: string }> = {
  PHOTO_LOGO: { label: "Photo + logo", hint: "Your photo, your details and the company logo" },
  PHOTO: { label: "With photo", hint: "Your photo beside your details" },
  CLASSIC: { label: "Classic", hint: "Your details as text, no pictures" },
  LOGO_BANNER: { label: "Logo banner", hint: "Your details over the company logo" },
  MINIMAL: { label: "Minimal", hint: "One line, good for replies" },
  CUSTOM: { label: "Custom", hint: "Write it yourself, with bold, links and lists" },
};

export function drawsPhoto(layout: SignatureLayout): boolean {
  return layout === "PHOTO" || layout === "PHOTO_LOGO";
}

export function drawsLogo(layout: SignatureLayout): boolean {
  return layout === "PHOTO_LOGO" || layout === "LOGO_BANNER" || layout === "CUSTOM";
}

/** What the editor holds: every text as a string, empty meaning "follow the source". */
export interface SignatureDraft {
  name: string;
  layout: SignatureLayout;
  fields: Record<keyof SignatureFields, string>;
  showPhoto: boolean;
  showLogo: boolean;
  showBookingLink: boolean;
  customBody: string;
  disclaimer: string;
  isDefault: boolean;
}

const NO_FIELDS: Record<keyof SignatureFields, string> = {
  name: "",
  title: "",
  phone: "",
  linkedinUrl: "",
  company: "",
  website: "",
  address: "",
};

/** A new signature of this layout: its pictures on, named for the layout, the default when it is the first. */
export function newDraft(layout: SignatureLayout, isFirst: boolean, ownName: string): SignatureDraft {
  return {
    name: LAYOUT_DETAILS[layout].label,
    layout,
    fields: { ...NO_FIELDS },
    showPhoto: drawsPhoto(layout),
    showLogo: drawsLogo(layout) && layout !== "CUSTOM",
    showBookingLink: false,
    customBody: layout === "CUSTOM" ? customBodyFor(ownName) : "",
    disclaimer: "",
    isDefault: isFirst,
  };
}

/** What a Custom signature starts from, so the editor never opens empty. */
export function customBodyFor(name: string): string {
  return `Kind regards,\n\n**${name}**`;
}

export function draftOf(signature: EmailSignature): SignatureDraft {
  const fields = { ...NO_FIELDS };
  for (const key of Object.keys(NO_FIELDS) as (keyof SignatureFields)[]) {
    fields[key] = signature.fields?.[key] ?? "";
  }
  return {
    name: signature.name,
    layout: signature.layout,
    fields,
    showPhoto: signature.showPhoto,
    showLogo: signature.showLogo,
    showBookingLink: signature.showBookingLink,
    customBody: signature.customBody ?? "",
    disclaimer: signature.disclaimer ?? "",
    isDefault: signature.isDefault,
  };
}

/** The request: blank as null, so a cleared field follows its source again. */
export function payloadOf(draft: SignatureDraft): SaveSignaturePayload {
  const fields = {} as SignatureFields;
  for (const key of Object.keys(NO_FIELDS) as (keyof SignatureFields)[]) {
    fields[key] = blankAsNull(draft.fields[key]);
  }
  return {
    name: draft.name.trim() || LAYOUT_DETAILS[draft.layout].label,
    layout: draft.layout,
    fields,
    showPhoto: draft.showPhoto,
    showLogo: draft.showLogo,
    showBookingLink: draft.showBookingLink,
    customBody: draft.layout === "CUSTOM" ? blankAsNull(draft.customBody) : null,
    disclaimer: blankAsNull(draft.disclaimer),
    isDefault: draft.isDefault,
  };
}

/** A copy for Duplicate: never the default, and named so the two can be told apart. */
export function duplicateOf(signature: EmailSignature): SaveSignaturePayload {
  return { ...payloadOf(draftOf(signature)), name: `${signature.name} (copy)`.slice(0, 60), isDefault: false };
}

function blankAsNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed === "" ? null : trimmed;
}
