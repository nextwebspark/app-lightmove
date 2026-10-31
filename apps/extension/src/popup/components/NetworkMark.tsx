/**
 * LinkedIn's own mark, from the downloadable asset apps/web's `NetworkMark` uses, unaltered: never recoloured, never
 * redrawn as a stroke glyph, never set in a disc. The panel is dark-only, so it is the white bug alone.
 */
export function LinkedInMark({ size, className }: { size: number; className?: string }) {
  return (
    <img
      src="/brand/linkedin-in-bug-white.png"
      alt=""
      width={size}
      height={Math.round(size / (840 / 779))}
      className={className ? `shrink-0 ${className}` : "shrink-0"}
    />
  );
}
