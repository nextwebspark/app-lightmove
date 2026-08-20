import { useState } from "react";

/**
 * A company's logo with an initial-letter fallback. The logo URL is an ETL snapshot that can rot,
 * so a broken image quietly degrades to the initial rather than a broken-image glyph.
 *
 * <p><b>A tile, not a bare image.</b> Corporate marks are overwhelmingly white or transparent PNGs,
 * which on a light row are invisible — a column of blanks that reads as missing data rather than as
 * a logo you cannot see. The muted ground and the hairline ring give every mark an edge, whatever
 * its own background.
 *
 * <p>`object-contain` rather than `cover`: a wordmark is wider than it is tall, and cropping it to a
 * square keeps a couple of letters and throws away the part that identifies the company.
 */
export function CompanyLogo({ name, logo, size }: { name: string; logo: string | null; size: number }) {
  const [failed, setFailed] = useState(false);
  if (!logo || failed) {
    return (
      <span
        aria-hidden="true"
        style={{ width: size, height: size, fontSize: size * 0.5 }}
        className="flex flex-none items-center justify-center rounded-[6px] bg-panel2 font-sans font-semibold text-text3 ring-1 ring-line-soft"
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
      className="flex-none rounded-[6px] bg-panel2 object-contain p-[2px] ring-1 ring-line-soft"
    />
  );
}
