import type { ReactNode } from "react";
import { cn } from "../lib/cn";

/** The Uncava mark that opens the popup's header — the same geometry as the extension icons. */
export function BrandTile({ className }: { className?: string }) {
  return (
    <span
      className={cn("grid h-[22px] w-[22px] shrink-0 place-items-center rounded-[5px] bg-text text-panel", className)}
      aria-hidden
    >
      <svg viewBox="86 86 340 340" width={22} height={22} fill="currentColor" stroke="currentColor">
        <path d="M256 131 332 174 256 216 180 174Z" strokeWidth="11" strokeLinejoin="round" />
        <path d="M256 241 332 285V336L256 380 180 336V285Z" fill="none" strokeWidth="11" strokeLinejoin="round" />
      </svg>
    </span>
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
