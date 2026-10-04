import type { CandidateTagColour } from "../api/types";

/** A tag's swatch as the pill draws it — the palette roles the Candidates mockup gives each colour. */
export const TAG_COLOURS: { value: CandidateTagColour; label: string; className: string; swatch: string }[] = [
  { value: "green", label: "Green", className: "text-u-direct bg-u-direct-tint", swatch: "bg-u-direct" },
  { value: "accent", label: "Accent", className: "text-u-accent bg-u-accent-tint", swatch: "bg-u-accent" },
  { value: "neutral", label: "Neutral", className: "text-u-text2 bg-u-raised", swatch: "bg-u-text3" },
  { value: "violet", label: "Violet", className: "text-u-chart-5 bg-u-chart-5/15", swatch: "bg-u-chart-5" },
  { value: "adjacent", label: "Blue", className: "text-u-adjacent bg-u-adjacent-tint", swatch: "bg-u-adjacent" },
  { value: "inferred", label: "Purple", className: "text-u-inferred bg-u-inferred-tint", swatch: "bg-u-inferred" },
];

const BY_VALUE = new Map(TAG_COLOURS.map((colour) => [colour.value, colour]));

/** Neutral for a colour this build has not heard of, so a tag the server stores always draws. */
export function tagClassName(colour: CandidateTagColour): string {
  return (BY_VALUE.get(colour) ?? TAG_COLOURS[2]).className;
}
