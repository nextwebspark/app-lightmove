import type { ReactNode } from "react";
import { toBrowsableUrl } from "../../lib/url";
import { GRID_ICON_BUTTON } from "./DataGrid";
import { Icon, ICONS } from "../layout/Icon";
import { NetworkMark } from "./NetworkMark";

/**
 * One icon link out to a company's presence somewhere — its site, LinkedIn, X. A dead icon is never
 * drawn: without a URL it renders nothing, or with `reserve` an empty slot the size of the button,
 * so a grid's Links cells keep every network in its own column whichever ones a row publishes.
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
 * The pair a company panel shows beside its name: its site and its LinkedIn, as icons.
 *
 * <p>Beside the name rather than in a tile of its own. An address is a way out to the company, not a
 * figure about it — and a tile is a frame around a figure, so framing two glyphs leaves an empty box
 * on every company that publishes neither. Here, that company takes no room at all: the pair renders
 * nothing when neither address survives {@link toBrowsableUrl}.
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
