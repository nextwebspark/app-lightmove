import { useState } from "react";

/**
 * A company's logo with an initial-letter fallback. The logo URL is an ETL snapshot that can rot,
 * so a broken image quietly degrades to the initial rather than a broken-image glyph.
 */
export function CompanyLogo({ name, logo, size }: { name: string; logo: string | null; size: number }) {
  const [failed, setFailed] = useState(false);
  if (!logo || failed) {
    return (
      <span
        aria-hidden="true"
        style={{ width: size, height: size, fontSize: size * 0.55 }}
        className="flex flex-none items-center justify-center rounded bg-panel2 font-sans font-semibold text-text3"
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
      className="flex-none rounded object-cover"
    />
  );
}
