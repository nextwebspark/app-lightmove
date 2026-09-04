import { cn } from "../lib/cn";

interface SubjectRowProps {
  name: string;
  detail: string | null;
  /** A person gets a circle, a company a rounded square — the design's own distinction. */
  shape: "circle" | "square";
  isReading?: boolean;
}

/**
 * Who or what is about to be captured, above the fields that describe them.
 *
 * Its height never changes. Collapsing to nothing while a name was being read is half of the jump
 * the consultant sees as flicker — the row leaves a placeholder of its own size instead.
 */
export function SubjectRow({ name, detail, shape, isReading = false }: SubjectRowProps) {
  const isNamed = Boolean(name.trim());
  return (
    <div className="mb-3.5 flex items-center gap-2.5" aria-busy={isReading || undefined}>
      <span
        aria-hidden
        className={cn(
          "grid h-10 w-10 shrink-0 place-items-center font-mono text-[13px] font-bold",
          shape === "circle" ? "rounded-full bg-amber-dim text-amber" : "rounded-[9px] bg-panel2 text-text2",
          isReading && "animate-pulse",
        )}
      >
        {isNamed ? initialsOf(name) : ""}
      </span>
      <span className="min-w-0 flex-1 overflow-hidden">
        {isNamed ? (
          <>
            <span className="block truncate text-[14.5px] font-semibold text-text">{name}</span>
            {detail && <span className="block truncate text-[11.5px] text-text3">{detail}</span>}
          </>
        ) : (
          <span
            aria-hidden
            className={cn("block h-[18px] w-3/5 rounded bg-panel2", isReading && "animate-pulse")}
          />
        )}
      </span>
    </div>
  );
}

function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  const first = parts[0]?.[0] ?? "?";
  const last = parts.length > 1 ? (parts[parts.length - 1][0] ?? "") : "";
  return (first + last).toUpperCase();
}
