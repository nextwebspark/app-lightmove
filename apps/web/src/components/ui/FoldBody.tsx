import type { ReactNode } from "react";
import { cn } from "../../lib/cn";

/**
 * What a heading folds away. It stays mounted while folded, animated shut with the grid-rows technique so
 * nothing is measured, and made inert so a folded control is neither tabbed into nor read out. The heading
 * that owns it carries `aria-expanded` and names this `id` in `aria-controls`.
 */
export function FoldBody({
  id,
  open,
  animated = true,
  children,
}: {
  id: string;
  open: boolean;
  /** False where the fold must land at once — a row that folds as it is picked up, say. */
  animated?: boolean;
  children: ReactNode;
}) {
  return (
    <div
      id={id}
      aria-hidden={!open}
      inert={!open}
      className={cn(
        "grid",
        animated && "transition-[grid-template-rows] duration-200 ease-out motion-reduce:transition-none",
      )}
      style={{ gridTemplateRows: open ? "1fr" : "0fr" }}
    >
      <div className="min-h-0 overflow-hidden">{children}</div>
    </div>
  );
}
