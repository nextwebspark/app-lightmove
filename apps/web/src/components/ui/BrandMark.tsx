import { cn } from "../../lib/cn";

/**
 * The Uncava mark — a rhombus over an isometric cube — on a rounded tile. Drawn in `currentColor`
 * over the text token so it inverts with the theme. Only the signed-out screens that have not moved
 * to `AuthLogo` still draw it.
 *
 * <p>The path data is the canonical geometry, the same two paths as `public/favicon.svg`,
 * `public/brand/uncava-app-icon-*.svg` and the extension's own `BrandMark`. It used to be an older
 * mark — a rhombus over a hexagon — which is how a signed-out screen came to show a different logo
 * from the one beside it.
 */
export function BrandMark({ size = 30, className }: { size?: number; className?: string }) {
  return (
    <span
      style={{ width: size, height: size, borderRadius: size * 0.215 }}
      className={cn("grid shrink-0 place-items-center bg-text text-panel", className)}
      aria-hidden
    >
      <svg viewBox="-66 -64 132 132" width={size} height={size} fill="currentColor" stroke="currentColor">
        <path d="M0 -48 L 32 -30 L 0 -12 L -32 -30 Z" stroke="none" />
        <path
          d="M-32 10 L 0 -8 L 32 10 L 0 28 Z M-32 10 V 32 L 0 50 L 32 32 V 10 M0 28 V 50"
          fill="none"
          strokeWidth="5.5"
          strokeLinejoin="round"
        />
      </svg>
    </span>
  );
}
