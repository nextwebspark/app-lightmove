import { Icon } from "../layout/Icon";
import { GRID_ICON_BUTTON } from "./DataGrid";

/**
 * One icon link out to a company's presence somewhere — its site, LinkedIn, X. Renders nothing when
 * there is no URL, so a Links cell collapses to the handful a company actually publishes rather than
 * showing four icons of which three are dead.
 *
 * <p>The company name is taken separately from the URL because it is only ever the accessible label:
 * four identical "open link" buttons in a row tell a screen-reader user nothing about which company
 * they belong to.
 *
 * <p>Anything that is not an absolute http(s) URL renders as nothing. Captured URLs are normalised
 * server-side, so this is the second line rather than the first — but it is the line that covers rows
 * written before that normalisation existed, and a bare host is the common case: as an `href` it is a
 * *relative* link, so `acme.com` would navigate inside the SPA rather than to the company.
 */
export function CompanyLink({
  url,
  icon,
  label,
  companyName,
}: {
  url: string | null;
  icon: string;
  label: string;
  companyName: string;
}) {
  if (!isBrowsable(url)) return null;
  return (
    <a
      href={url}
      target="_blank"
      rel="noopener noreferrer"
      title={label}
      aria-label={`${companyName} on ${label}`}
      className={GRID_ICON_BUTTON}
    >
      <Icon d={icon} size={13} />
    </a>
  );
}

/** Absolute, and a scheme a browser will follow — which `javascript:` is not. */
function isBrowsable(url: string | null): url is string {
  if (!url) return false;
  try {
    const { protocol } = new URL(url);
    return protocol === "http:" || protocol === "https:";
  } catch {
    // Not absolute, so not resolvable without borrowing the SPA's own origin.
    return false;
  }
}
