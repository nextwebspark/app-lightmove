import type { CompanySearchOrder } from "../api/companiesApi";
import type { CompanyRef, CompanySearchResult } from "../api/types";
import { companyKeyOf } from "../lib/companyKey";
import { CompanyLogo } from "./CompanyLogo";
import { CompanySearchCombobox } from "./CompanySearchCombobox";

/**
 * A strategy company list — the shared shape of Target List Seeding and Off-limits: one title, one
 * subtitle, a universe search on top and the picked companies as rows beneath. Off-limits carries
 * the red accent the mockup gives everything exclusionary; there is no Required/Preferred toggle —
 * an off-limits entry is always a hard exclusion.
 */
export function CompanyListPanel({
  panelKey,
  title,
  subtitle,
  companies,
  excludedKeys,
  browseSectors,
  browseOrder,
  accent,
  onAdd,
  onRemove,
}: {
  panelKey: string;
  title: string;
  subtitle: string;
  companies: CompanyRef[];
  /** Keys the combobox must not offer — this list's own entries plus the other list's. */
  excludedKeys: Set<string>;
  browseSectors: string[];
  browseOrder: CompanySearchOrder;
  accent?: "red";
  onAdd: (company: CompanySearchResult) => void;
  onRemove: (company: CompanyRef) => void;
}) {
  return (
    <div className="min-h-[360px] rounded-[10px] border border-line-soft bg-panel2 px-5 py-[18px]">
      <div className="mb-1 flex items-center gap-2">
        {accent === "red" && (
          <span aria-hidden="true" className="h-2 w-2 flex-none rounded-full bg-red" />
        )}
        <span className="font-sans text-[13px] font-semibold">{title}</span>
      </div>
      <div className="mb-1.5 mt-1 font-mono text-[11.5px] text-text3">{subtitle}</div>

      <div className="mb-2.5 mt-3.5">
        <CompanySearchCombobox
          listId={`${panelKey}-typeahead`}
          excludedKeys={excludedKeys}
          browseSectors={browseSectors}
          browseOrder={browseOrder}
          onPick={onAdd}
        />
      </div>

      <div className="flex flex-col gap-1.5">
        {companies.map((company) => (
          <div
            key={companyKeyOf(company)}
            className="flex items-center gap-2.5 rounded-lg border border-line-soft bg-panel px-3 py-2"
          >
            <CompanyLogo name={company.name} logo={company.logo} size={20} />
            <div className="min-w-0 flex-1">
              <div className="flex items-baseline gap-2">
                <span className="truncate font-sans text-[13px] font-medium text-text">
                  {company.name}
                </span>
                <span className="flex-none font-mono text-[10.5px] text-text3">
                  {[company.hqCity, company.hqCountry].filter(Boolean).join(", ")}
                </span>
              </div>
              {company.slogan && (
                <div className="truncate text-[12px] text-text3">{company.slogan}</div>
              )}
            </div>
            <button
              type="button"
              aria-label={`Remove ${company.name}`}
              onClick={() => onRemove(company)}
              className="ml-auto flex-none px-1 py-0.5 text-text3 transition hover:text-red"
            >
              ✕
            </button>
          </div>
        ))}
        {companies.length === 0 && (
          <div className="px-0.5 py-1.5 font-mono text-[12px] text-text3">None yet.</div>
        )}
      </div>
    </div>
  );
}
