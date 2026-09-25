import type { ReactNode } from "react";
import { cn } from "../../lib/cn";
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
  cornerActions,
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
  /** Controls on their own row under the name, such as Edit: beside it they squeezed the name column. */
  action?: ReactNode;
  /** Icon buttons in the top corner, drawn beside Close — the panel's actions, kept off the name row. */
  cornerActions?: ReactNode;
  onClose: () => void;
}) {
  return (
    <div className="relative flex-none border-b border-u-border px-5 py-4">
      <DrawerCloseButton onClose={onClose} />
      {cornerActions && (
        <span className="absolute end-12 top-3.5 flex items-center gap-1">{cornerActions}</span>
      )}

      <div className={cn("flex items-start gap-3", cornerActions ? "pe-36" : "pe-8")}>
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
          <p className="mt-1 font-mono text-[11.5px] text-u-text3">
            {context.filter(Boolean).join(" · ") || "Nothing recorded about where it sits"}
          </p>
          {action && <div className="mt-3">{action}</div>}
        </div>
      </div>
    </div>
  );
}
