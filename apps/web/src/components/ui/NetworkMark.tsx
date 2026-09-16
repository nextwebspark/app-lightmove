import { cn } from "../../lib/cn";

/**
 * A network's own mark — LinkedIn, X or Facebook — drawn wherever we link out to a profile or page.
 *
 * <p>The files under `public/brand/` are each network's downloadable assets, used as its guidelines
 * allow: unaltered, with clear space, in a colourway it publishes. Never recolour one through
 * `currentColor`, never put one in a grey disc, never redraw one as a stroke glyph. Each network
 * names one file for the light theme and one for the dark, swapped through the `dark` variant.
 * LinkedIn and X publish solid black and white; Meta publishes no black "f", so Facebook is its
 * primary blue on the light theme and its white secondary on the dark. LinkedIn's stated floor is
 * 21px, which the Contact row keeps; beside a name in a drawer header and in a grid's Links cell a
 * mark is drawn at 14–16px by the product's own call, so it weighs the same as the glyphs around it.
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

export type Network = "linkedin" | "x" | "facebook";

/** Width over height of each file, so a mark keeps its shape at any rendered width. */
const ASSETS: Record<Network, { light: string; dark: string; ratio: number }> = {
  linkedin: {
    light: "/brand/linkedin-in-bug-black.png",
    dark: "/brand/linkedin-in-bug-white.png",
    ratio: 840 / 779,
  },
  x: { light: "/brand/x-logo-black.png", dark: "/brand/x-logo-white.png", ratio: 2400 / 2453 },
  facebook: {
    light: "/brand/facebook-logo-primary.png",
    dark: "/brand/facebook-logo-secondary.png",
    ratio: 1,
  },
};
