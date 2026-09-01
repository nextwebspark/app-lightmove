import type { ReactNode } from "react";
import { cn } from "../lib/cn";

/** The amber logo tile that opens the popup's header, per Extension.dc.html. */
export function BrandTile({ className }: { className?: string }) {
  return (
    <span
      className={cn(
        "grid h-[22px] w-[22px] place-items-center rounded-md bg-amber-btn",
        "font-mono text-[11px] font-bold text-on-amber",
        className,
      )}
      aria-hidden
    >
      L
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
