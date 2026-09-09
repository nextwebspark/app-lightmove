import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button } from "../../../components/ui";
import { CompanyDrawerHeader } from "../../../components/ui/CompanyDrawerHeader";
import { DetailGrid, DetailPill, DetailTile, DrawerSection } from "../../../components/ui/DetailList";
import { Drawer } from "../../../components/ui/Drawer";
import { cn } from "../../../lib/cn";
import { formatDate, formatMoney } from "../../../lib/format";
import { CompanyFactsSections } from "../../triage/components/CompanyFactsSections";
import type { TriageCompanyStatus } from "../../triage/api/types";
import { TRIAGE_STAGES } from "../../triage/lib/triageStages";
import type { CompanyResult } from "../api/types";

/** Beyond this the tag rows stop being a summary and start being the whole panel. */
const TAG_LIMIT = 12;

/**
 * One company of the market, read before it is decided on. The facts here are the export's and none
 * of them are editable — the four buttons at the foot are the only marks a mandate can make.
 */
export function MarketCompanyDrawer({
  company,
  onClose,
  onTriage,
  onOffLimits,
  barring,
}: {
  /** The company being read, or null when the panel is closed. */
  company: CompanyResult | null;
  onClose: () => void;
  onTriage: (company: CompanyResult, status: TriageCompanyStatus) => void;
  onOffLimits: (company: CompanyResult) => void;
  barring: boolean;
}) {
  if (!company) return null;

  // Apollo spells the same thing into both lists — "saas" is a keyword and a technology — and a
  // repeated tag is both a duplicate React key and an inflated "+N more".
  const tags = [...new Set([...company.keywords, ...company.technologies])];
  const hasFunding =
    company.totalFunding !== null ||
    company.latestFunding !== null ||
    company.latestFundingAmount !== null ||
    company.lastRaisedAt !== null;

  return (
    <Drawer open onClose={onClose} wide label={company.companyName}>
      <CompanyDrawerHeader
        companyName={company.companyName}
        logoUrl={company.logoUrl}
        website={company.website}
        linkedinUrl={company.companyLinkedinUrl}
        context={[company.industry, company.companyCity, company.companyCountry]}
        onClose={onClose}
      />

      <div className="min-h-0 flex-1 overflow-y-auto px-5">
        <CompanyFactsSections company={company} />

        <DrawerSection title="Where it sits">
          <DetailGrid>
            <DetailTile label="State" value={company.companyState} />
            <DetailTile label="Phone" value={company.companyPhone} />
            <DetailTile label="Address" value={company.companyAddress} full />
            <DetailTile label="Parent company" value={company.parentCompany} full />
          </DetailGrid>
        </DrawerSection>

        {hasFunding && (
          <DrawerSection title="Funding">
            <DetailGrid>
              <DetailTile label="Total raised" value={formatMoney(company.totalFunding)} />
              <DetailTile label="Latest round" value={company.latestFunding} />
              <DetailTile label="Latest amount" value={formatMoney(company.latestFundingAmount)} />
              <DetailTile label="Last raised" value={formatDate(company.lastRaisedAt)} />
            </DetailGrid>
          </DrawerSection>
        )}

        {tags.length > 0 && (
          <DrawerSection title="Tags">
            <div className="flex flex-wrap gap-1.5">
              {tags.slice(0, TAG_LIMIT).map((tag) => (
                <DetailPill key={tag} label={tag} />
              ))}
              {tags.length > TAG_LIMIT && (
                <span className="font-mono text-[11px] text-text3">
                  +{tags.length - TAG_LIMIT} more
                </span>
              )}
            </div>
          </DrawerSection>
        )}
      </div>

      <div className="flex flex-none flex-wrap items-center gap-2 border-t border-line-soft px-5 py-3">
        {TRIAGE_STAGES.map((stage) => (
          <Button
            key={stage.status}
            type="button"
            variant="secondary"
            // Declined reads as the destructive one on the bulk bar over this same grid.
            className={cn(stage.status === "declined" && "text-red")}
            onClick={() => onTriage(company, stage.status)}
          >
            <Icon d={stage.icon} size={14} />
            {stage.label}
          </Button>
        ))}
        {/* Off limits bars the company from every search this mandate runs rather than filing it at
            one stage, which is why it sits apart from the three. */}
        <Button
          type="button"
          variant="secondary"
          className="ms-auto text-red"
          disabled={barring}
          onClick={() => onOffLimits(company)}
        >
          <Icon d={ICONS.lock} size={14} />
          Off limits
        </Button>
      </div>
    </Drawer>
  );
}
