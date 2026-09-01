import type { ReactNode } from "react";
import { cn } from "../lib/cn";

/** The uppercase micro-label above each section of the form. */
export function SectionLabel({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div
      className={cn(
        "font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-text3",
        className,
      )}
    >
      {children}
    </div>
  );
}
