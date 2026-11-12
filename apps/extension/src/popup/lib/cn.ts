import { clsx, type ClassValue } from "clsx";
import { extendTailwindMerge } from "tailwind-merge";

/*
 * apps/web's shipped bug, kept out: unregistered, `text-note` reads to twMerge as a text *colour*, so a colour after
 * it dropped the size. Every step of theme.css's type scale is listed here.
 */
const twMerge = extendTailwindMerge({
  extend: {
    theme: {
      text: ["eyebrow", "meta", "note", "body", "lead"],
    },
  },
});

/** Conditional Tailwind classes, with later utilities winning over earlier ones. */
export function cn(...classes: ClassValue[]): string {
  return twMerge(clsx(classes));
}
