import { DetailGrid, DetailTile, DrawerSection } from "../../../components/ui/DetailList";
import { ExpandableText } from "../../../components/ui/ExpandableText";
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
 * A company's own facts, read-only — one component for the held company's panel and the Add form's preview, so the two
 * never describe the same company differently.
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
        <ExpandableText text={company.shortDescription ?? emptyDescription} />
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
