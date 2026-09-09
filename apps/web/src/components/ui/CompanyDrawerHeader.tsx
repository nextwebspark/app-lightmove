import type { ReactNode } from "react";
import { CompanyLinks } from "./CompanyLink";
import { CompanyLogo } from "./CompanyLogo";
import { DrawerCloseButton } from "./Drawer";

/**
 * The top of every company panel: mark, name, its two links, a line of context, and whatever badges
 * or action the panel adds. Shared so the market's company and the mandate's own open under the same
 * header rather than two that drifted apart.
 */
export function CompanyDrawerHeader({
  companyName,
  logoUrl,
  website,
  linkedinUrl,
  context,
  badges,
  action,
  onClose,
}: {
  companyName: string;
  logoUrl: string | null;
  website: string | null;
  linkedinUrl: string | null;
  /** Industry, city, country — joined, minus whatever this company does not carry. */
  context: (string | null)[];
  /** Pills sitting beside the name — a stage, a source. */
  badges?: ReactNode;
  /** A control at the end of the row, such as Edit. */
  action?: ReactNode;
  onClose: () => void;
}) {
  return (
    <div className="relative flex-none border-b border-line-soft px-5 py-4">
      <DrawerCloseButton onClose={onClose} />

      <div className="flex items-start gap-3 pe-8">
        <CompanyLogo name={companyName} logo={logoUrl} size={44} />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            {/* Name and links are one wrapping item: a long name pushed them onto the badge row,
                where a bare globe reads as another badge rather than as this company's site. */}
            <span className="flex items-center gap-1.5">
              <h2 className="font-sans text-base font-semibold">{companyName}</h2>
              <CompanyLinks
                companyName={companyName}
                website={website}
                linkedinUrl={linkedinUrl}
              />
            </span>
            {badges}
          </div>
          <p className="mt-1 font-mono text-[11.5px] text-text3">
            {context.filter(Boolean).join(" · ") || "Nothing recorded about where it sits"}
          </p>
        </div>
        {action}
      </div>
    </div>
  );
}
