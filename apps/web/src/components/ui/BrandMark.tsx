import { cn } from "../../lib/cn";

/**
 * The Uncava mark — a rhombus over a hexagon — on a rounded tile. Drawn in `currentColor` over the
 * text token so it inverts with the theme. Only the signed-out screens that have not moved to
 * `AuthLogo` still draw it.
 */
export function BrandMark({ size = 30, className }: { size?: number; className?: string }) {
  return (
    <span
      style={{ width: size, height: size, borderRadius: size * 0.215 }}
      className={cn("grid shrink-0 place-items-center bg-text text-panel", className)}
      aria-hidden
    >
      <svg viewBox="86 86 340 340" width={size} height={size} fill="currentColor" stroke="currentColor">
        <path d="M256 131 332 174 256 216 180 174Z" strokeWidth="11" strokeLinejoin="round" />
        <path d="M256 241 332 285V336L256 380 180 336V285Z" fill="none" strokeWidth="11" strokeLinejoin="round" />
      </svg>
    </span>
  );
}
