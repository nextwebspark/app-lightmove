import { cn } from "../lib/cn";

interface SubjectRowProps {
  name: string;
  detail: string | null;
  /** A person gets a circle, a company a rounded square — the design's own distinction. */
  shape: "circle" | "square";
}

/** Who or what is about to be captured, above the fields that describe them. */
export function SubjectRow({ name, detail, shape }: SubjectRowProps) {
  if (!name.trim()) {
    return null;
  }
  return (
    <div className="mb-3.5 flex items-center gap-2.5">
      <span
        aria-hidden
        className={cn(
          "grid h-10 w-10 shrink-0 place-items-center font-mono text-[13px] font-bold",
          shape === "circle" ? "rounded-full bg-amber-dim text-amber" : "rounded-[9px] bg-panel2 text-text2",
        )}
      >
        {initialsOf(name)}
      </span>
      <span className="overflow-hidden">
        <span className="block truncate text-[14.5px] font-semibold text-text">{name}</span>
        {detail && <span className="block truncate text-[11.5px] text-text3">{detail}</span>}
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
