import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

/**
 * Joins class names, letting a caller's utility override the component's default.
 *
 * Tailwind resolves a conflict between two utilities of the same kind by their order in the generated
 * stylesheet, not by their order in the class attribute — so a component that hardcodes `w-full` and
 * then appends `w-[130px]` from a caller does not reliably end up 130px wide. It ends up whichever the
 * stylesheet happens to define last, which is a coin toss the caller cannot see or fix.
 *
 * `twMerge` resolves those conflicts by class *group*, keeping the last one: `w-full w-[130px]`
 * becomes `w-[130px]`. Every component that accepts a `className` must route it through here.
 */
export function cn(...classes: ClassValue[]): string {
  return twMerge(clsx(classes));
}
