import { cn } from "../../lib/cn";

/**
 * A network's own mark, drawn wherever we link out. The `public/brand/` files are each network's assets, used as its
 * guidelines allow: never recoloured via `currentColor`, put in a disc or redrawn; light and dark files swap via `dark`.
 * Facebook is its blue on light and white on dark (Meta publishes no black "f").
 */
export function NetworkMark({
  network,
  size,
  className,
}: {
  network: Network;
  /** Rendered width in px. */
  size: number;
  className?: string;
}) {
  const { light, dark, ratio } = ASSETS[network];
  const height = Math.round(size / ratio);
  return (
    <>
      <img
        src={light}
        alt=""
        width={size}
        height={height}
        className={cn("shrink-0 dark:hidden", className)}
      />
      <img
        src={dark}
        alt=""
        width={size}
        height={height}
        className={cn("hidden shrink-0 dark:block", className)}
      />
    </>
  );
}

export type Network = "linkedin" | "x" | "facebook" | "whatsapp";

/** Width over height of each file, so a mark keeps its shape at any rendered width. */
const ASSETS: Record<Network, { light: string; dark: string; ratio: number }> = {
  linkedin: {
    light: "/brand/linkedin-in-bug-black.png",
    dark: "/brand/linkedin-in-bug-white.png",
    ratio: 840 / 779,
  },
  whatsapp: {
    light: "/brand/whatsapp-glyph-black.svg",
    dark: "/brand/whatsapp-glyph-white.svg",
    ratio: 1,
  },
  x: { light: "/brand/x-logo-black.png", dark: "/brand/x-logo-white.png", ratio: 2400 / 2453 },
  facebook: {
    light: "/brand/facebook-logo-primary.png",
    dark: "/brand/facebook-logo-secondary.png",
    ratio: 1,
  },
};
