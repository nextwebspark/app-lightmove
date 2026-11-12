import type { ReactNode } from "react";
import { toBrowsableUrl } from "../../lib/url";
import { GRID_ICON_BUTTON } from "./DataGrid";
import { Icon, ICONS } from "../layout/Icon";
import { NetworkMark } from "./NetworkMark";

/**
 * One icon link to a company's site or network; no URL renders nothing (or, with `reserve`, an empty slot). The name is
 * the accessible label only. {@link toBrowsableUrl} promotes a bare host to https — many Apollo rows publish one.
 */
export function CompanyLink({
  url,
  icon,
  label,
  companyName,
  reserve = false,
}: {
  url: string | null;
  /** The glyph itself: a stroke {@link Icon} for a site, a network's own mark for a network. */
  icon: ReactNode;
  label: string;
  companyName: string;
  /** Hold the button's space when there is no URL, so the icons beside it stay in their columns. */
  reserve?: boolean;
}) {
  const href = toBrowsableUrl(url);
  if (!href) return reserve ? <span aria-hidden className="size-9 flex-none lg:size-6" /> : null;
  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      title={label}
      aria-label={`${companyName} on ${label}`}
      className={GRID_ICON_BUTTON}
    >
      {icon}
    </a>
  );
}

/**
 * A company panel's site and LinkedIn icons beside its name; renders nothing when neither survives {@link toBrowsableUrl}.
 */
export function CompanyLinks({
  companyName,
  website,
  linkedinUrl,
}: {
  companyName: string;
  website: string | null;
  linkedinUrl: string | null;
}) {
  if (!toBrowsableUrl(website) && !toBrowsableUrl(linkedinUrl)) return null;
  return (
    <span className="flex flex-none items-center">
      <CompanyLink
        url={website}
        icon={<Icon d={ICONS.globe} size={13} />}
        label="website"
        companyName={companyName}
      />
      <CompanyLink
        url={linkedinUrl}
        icon={<NetworkMark network="linkedin" size={14} />}
        label="LinkedIn"
        companyName={companyName}
      />
    </span>
  );
}
