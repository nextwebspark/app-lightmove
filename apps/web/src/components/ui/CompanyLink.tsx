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
  if (!url) return null;
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
