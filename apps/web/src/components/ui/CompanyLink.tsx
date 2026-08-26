import { Icon } from "../layout/Icon";
import { toBrowsableUrl } from "../../lib/url";
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
 * <p>The URL goes through {@link toBrowsableUrl}, which is the client mirror of the server's capture
 * normalisation: a bare host gains `https://` and anything a browser should not follow renders as
 * nothing. Promoting rather than dropping matters here — plenty of Apollo rows publish a bare host,
 * and refusing those would lose the icon on a company whose site is perfectly real.
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
  const href = toBrowsableUrl(url);
  if (!href) return null;
  return (
    <a
      href={href}
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
