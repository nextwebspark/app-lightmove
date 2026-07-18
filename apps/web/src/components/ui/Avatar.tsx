import { initials } from "../../lib/format";
import { cn } from "../../lib/cn";

/**
 * An initials circle with a deterministic accent — the same person is the same colour on every
 * screen, hashed from their id exactly so, matching the mockups' per-member tints.
 */
const ACCENTS = [
  "bg-sky-dim text-sky",
  "bg-amber-dim text-amber",
  "bg-green-dim text-green",
  "bg-red-dim text-red",
  "bg-panel2 text-text2",
] as const;

const SIZES = {
  sm: "size-6 text-[10px]",
  md: "size-[26px] text-[10px]",
  lg: "size-[30px] text-[10px]",
} as const;

export function Avatar({
  id,
  name,
  size = "md",
  className,
}: {
  id: string;
  name: string;
  size?: keyof typeof SIZES;
  className?: string;
}) {
  let hash = 0;
  for (const char of id) hash = (hash * 31 + char.charCodeAt(0)) >>> 0;

  return (
    <span
      title={name}
      className={cn(
        "grid shrink-0 place-items-center rounded-full font-mono font-semibold",
        SIZES[size],
        ACCENTS[hash % ACCENTS.length],
        className,
      )}
    >
      {initials(name)}
    </span>
  );
}
