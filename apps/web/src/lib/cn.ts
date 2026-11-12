import { clsx, type ClassValue } from "clsx";
import { extendTailwindMerge } from "tailwind-merge";

/*
 * Shipped bug: unregistered, `text-note` read to twMerge as a text *colour*, so any colour after it
 * (`cn("text-note …", "text-u-inferred")`) silently dropped the size and the text fell back to the
 * body's 14px. Every step of tokens.css's type scale, tracking and leading must be listed here.
 */
const twMerge = extendTailwindMerge<"type-role">({
  extend: {
    theme: {
      text: ["eyebrow", "meta", "note", "body", "lead", "subhead", "title", "figure"],
      tracking: ["label", "tag", "title", "figure"],
      leading: ["narrative"],
      font: ["brand"],
    },
    classGroups: {
      "type-role": [
        { type: ["label", "micro-label", "summary-label", "tag", "title", "heading", "figure", "figure-input"] },
      ],
    },
  },
});

/**
 * Joins class names with `twMerge`, so a caller's utility overrides the default: Tailwind resolves conflicts by
 * stylesheet order, not attribute order. Every component accepting `className` must route it through here.
 */
export function cn(...classes: ClassValue[]): string {
  return twMerge(clsx(classes));
}
