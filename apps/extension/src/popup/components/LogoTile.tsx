import { useState } from "react";
import { cn } from "../lib/cn";
import { initials } from "../lib/initials";

const SIZES = {
  sm: "size-7 text-[10px]",
  md: "size-8 text-[11px]",
  lg: "size-11 text-[14px]",
} as const;

/**
 * A company's logo on a white tile, or its initials where there is none — a client on a position, an employer beside a
 * person, a company being filed. A logo is a provider's URL that may expire, so a broken one falls back to the initials.
 */
export function LogoTile({
  name,
  src,
  size = "md",
  className,
}: {
  name: string;
  src: string | null | undefined;
  size?: keyof typeof SIZES;
  className?: string;
}) {
  const [failedSrc, setFailedSrc] = useState<string | null>(null);
  const shape = cn("grid flex-none place-items-center overflow-hidden rounded-[7px] border border-u-border", SIZES[size], className);
  if (src && failedSrc !== src) {
    return (
      <span className={cn(shape, "bg-white")}>
        <img
          src={src}
          alt=""
          referrerPolicy="no-referrer"
          onError={() => setFailedSrc(src)}
          className="size-full object-contain p-[3px]"
        />
      </span>
    );
  }
  return <span className={cn(shape, "bg-u-raised font-mono font-bold text-u-text2")}>{initials(name)}</span>;
}
