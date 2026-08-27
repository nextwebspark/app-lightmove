import { DetailGrid, DetailTile, DrawerSection } from "../../../components/ui/DetailList";
import { formatMoney } from "../../../lib/format";

/**
 * The facts about a company itself, as opposed to what a mandate has decided about it. Structural
 * rather than named after either type that satisfies it: a triaged row and a market row carry the
 * same facts under the same names, and this reads both.
 */
export interface CompanyFacts {
  companyName: string;
  shortDescription: string | null;
  annualRevenue: number | null;
  numEmployees: number | null;
  foundedYear: number | null;
  industry: string | null;
  website: string | null;
  companyLinkedinUrl: string | null;
}

/**
 * A company's own facts, read-only — what the panel shows for a company the mandate holds, and what
 * the Add form shows for one still being picked out of the market.
 *
 * <p>One component for both, because they are the same company either side of the decision to take
 * it. Two would let the preview and the panel describe it differently, which is the one thing a
 * consultant comparing them would read as the record having changed.
 */
export function CompanyFactsSections({
  company,
  emptyDescription = "No description was captured for this company.",
}: {
  company: CompanyFacts;
  /** What stands in for a company nobody has described. */
  emptyDescription?: string;
}) {
  return (
    <>
      <DrawerSection title="About">
        <p className="text-[13px]/[1.6] text-text2">
          {company.shortDescription ?? emptyDescription}
        </p>
      </DrawerSection>

      <DrawerSection title="Scale snapshot">
        <DetailGrid>
          <DetailTile label="Revenue" value={formatMoney(company.annualRevenue)} />
          <DetailTile label="Employees" value={company.numEmployees?.toLocaleString() ?? null} />
          <DetailTile label="Founded" value={company.foundedYear?.toString() ?? null} />
          <DetailTile label="Sector" value={company.industry} />
        </DetailGrid>
      </DrawerSection>
    </>
  );
}
