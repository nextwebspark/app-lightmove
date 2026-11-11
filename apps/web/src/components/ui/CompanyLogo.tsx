import { useState } from "react";

/**
 * A company's logo, falling back to its initial when the snapshot URL rots. A tile, so white or transparent marks keep
 * an edge; `object-contain`, so a wordmark is not cropped to a couple of letters.
 */
export function CompanyLogo({ name, logo, size }: { name: string; logo: string | null; size: number }) {
  const [failed, setFailed] = useState(false);
  if (!logo || failed) {
    return (
      <span
        aria-hidden="true"
        style={{ width: size, height: size, fontSize: size * 0.5 }}
        className="flex flex-none items-center justify-center rounded-[6px] bg-u-raised font-sans font-semibold text-u-text3 ring-1 ring-u-border"
      >
        {name.charAt(0).toUpperCase()}
      </span>
    );
  }
  return (
    <img
      src={logo}
      alt=""
      width={size}
      height={size}
      onError={() => setFailed(true)}
      // A table page is 25 of these against as many third-party hosts.
      loading="lazy"
      decoding="async"
      // The host is named by pipeline data, so don't hand it the URL of the screen it loaded from.
      referrerPolicy="no-referrer"
      className="flex-none rounded-[6px] bg-u-raised object-contain p-[2px] ring-1 ring-u-border"
    />
  );
}
