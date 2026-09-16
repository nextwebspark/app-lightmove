import type { ReactNode } from "react";
import { cn } from "../lib/cn";

/** The Uncava app icon without its tile, drawn in the text token — the same mark as the SPA's top bar. */
export function BrandMark({ className }: { className?: string }) {
  return (
    <svg
      viewBox="-36 -51 72 104"
      className={cn("h-6 w-auto shrink-0 text-text", className)}
      fill="none"
      stroke="currentColor"
      aria-hidden
    >
      <path d="M-32 10 L 0 -8 L 32 10 L 0 28 Z M-32 10 V 32 L 0 50 L 32 32 V 10 M0 28 V 50" strokeWidth="2.6" strokeLinejoin="round" />
      <path d="M0 -48 L 32 -30 L 0 -12 L -32 -30 Z" fill="currentColor" stroke="none" />
    </svg>
  );
}

/** A person's initials in a tinted circle — the header avatar. Never an image; there is none to load. */
export function InitialsAvatar({ name }: { name: string }) {
  return (
    <span
      className="grid h-[26px] w-[26px] place-items-center rounded-full bg-green-dim font-mono text-[10.5px] font-bold text-green"
      title={name}
    >
      {initialsOf(name)}
    </span>
  );
}

function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) {
    return "?";
  }
  const first = parts[0][0] ?? "";
  const last = parts.length > 1 ? (parts[parts.length - 1][0] ?? "") : "";
  return (first + last).toUpperCase();
}

/**
 * The shell every screen renders inside: the side panel, whatever width the consultant drags it to.
 *
 * Pinned to the viewport rather than sized by it. `h-screen` left the footer — the mandate select and
 * Save — dependent on every ancestor passing a height down, and in the panel it did not arrive.
 */
export function PopupShell({ children }: { children: ReactNode }) {
  return (
    <div className="fixed inset-0 flex min-w-[320px] flex-col overflow-hidden bg-panel">{children}</div>
  );
}
