import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

/** Conditional Tailwind classes, with later utilities winning over earlier ones. */
export function cn(...classes: ClassValue[]): string {
  return twMerge(clsx(classes));
}
