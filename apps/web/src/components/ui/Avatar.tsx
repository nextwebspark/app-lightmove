import { useState } from "react";
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
  /** The identity tile on Settings → Profile, where the person is the subject of the screen. */
  xl: "size-[52px] text-[17px]",
} as const;

export function Avatar({
  id,
  name,
  src,
  size = "md",
  className,
}: {
  id: string;
  name: string;
  /** A picture from the user's identity provider, if they signed in with one. */
  src?: string | null;
  size?: keyof typeof SIZES;
  className?: string;
}) {
  // What we hold is the provider's CDN URL, not a copy of the image, and LinkedIn's expire within
  // weeks. So a broken picture is expected rather than exceptional: fall back to the initials the
  // rest of the product uses instead of leaving a torn-image icon on the roster.
  //
  // The failure is remembered per URL, not as a boolean: a re-stamped picture arriving on the next
  // sign-in must get its chance, and Topbar's avatar outlives every one of them.
  const [failedSrc, setFailedSrc] = useState<string | null>(null);

  if (src && failedSrc !== src) {
    return (
      <img
        src={src}
        // Named, not decorative: in the projects table and the topbar this image is the only thing
        // identifying the person, and the initials it replaces were readable.
        alt={name}
        title={name}
        loading="lazy"
        referrerPolicy="no-referrer"
        onError={() => setFailedSrc(src)}
        className={cn("shrink-0 rounded-full object-cover", SIZES[size], className)}
      />
    );
  }

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
