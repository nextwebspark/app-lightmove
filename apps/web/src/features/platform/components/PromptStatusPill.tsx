import { cn } from "../../../lib/cn";

/** Shipped, or which saved version of a prompt is in use. */
export function PromptStatusPill({ activeVersion }: { activeVersion: number | null }) {
  return (
    <span
      className={cn(
        "inline-flex rounded-full px-2.5 py-0.5 text-meta font-medium",
        activeVersion === null ? "bg-u-raised text-u-text2" : "bg-u-accent-tint text-u-accent",
      )}
    >
      {activeVersion === null ? "Shipped" : `Changed · v${activeVersion}`}
    </span>
  );
}
