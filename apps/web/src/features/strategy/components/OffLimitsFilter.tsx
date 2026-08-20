import type { CompanyRef, CompanySuggestion } from "../api/types";
import { Icon } from "../../../components/layout/Icon";
import { CompanySearchCombobox } from "./CompanySearchCombobox";

const CLOSE = "M18 6 6 18M6 6l12 12";

/**
 * The Off-limits panel: companies this mandate may not approach, barred by name.
 *
 * <p>The panel states the rule it enforces, and it enforces exactly that rule. There is no
 * show-anyway toggle: a barred company is dropped from every filtered read, from the count above the
 * table, and from "Add all to Universe". A screen that offered to reveal them would make the list
 * advisory, and an off-limits list is the one filter on this rail that is a commitment rather than
 * a preference — usually a client's own subsidiaries, or a firm the mandate has an agreement about.
 *
 * <p>Entries are picked from the universe, never typed. A free-text bar would silently fail to match
 * the company it named — the exclusion is by account id, and a name that resolves to nothing bars
 * nothing while looking like it bars something.
 */
export function OffLimitsFilter({
  companies,
  onChange,
}: {
  companies: CompanyRef[];
  onChange: (apolloAccountIds: string[]) => void;
}) {
  const excludedIds = new Set(companies.map((company) => company.apolloAccountId));

  const add = (company: CompanySuggestion) => {
    if (excludedIds.has(company.apolloAccountId)) return;
    onChange([...companies.map((entry) => entry.apolloAccountId), company.apolloAccountId]);
  };

  const remove = (apolloAccountId: string) => {
    onChange(
      companies
        .map((entry) => entry.apolloAccountId)
        .filter((entry) => entry !== apolloAccountId),
    );
  };

  return (
    <div className="flex flex-col gap-[14px]">
      <p className="font-sans text-[12px] leading-relaxed text-text3">
        Companies added here will be completely excluded from your active sourcing search results.
      </p>

      <CompanySearchCombobox
        listId="off-limits-search"
        excludedIds={excludedIds}
        onPick={add}
      />

      <div>
        <div className="mb-2 font-sans text-[10px] font-semibold tracking-[1px] text-text3">
          EXCLUDED ({companies.length})
        </div>
        {companies.length === 0 ? (
          <p className="font-sans text-[12px] text-text3">Nothing excluded yet.</p>
        ) : (
          <div className="flex flex-wrap gap-[6px]">
            {companies.map((company) => (
              <button
                key={company.apolloAccountId}
                type="button"
                title="Remove"
                aria-label={`Remove ${company.companyName}`}
                onClick={() => remove(company.apolloAccountId)}
                className="inline-flex items-center gap-[6px] rounded-[4px] bg-amber-dim px-2 py-1 shadow-[inset_0_0_0_1px_var(--color-amber-dim)]"
              >
                <span className="font-sans text-[11px] font-medium text-amber">
                  {company.companyName}
                </span>
                <Icon d={CLOSE} size={9} className="text-amber" />
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
