import type { CompanyRef, CompanySuggestion } from "../api/types";
import { CompanySearchCombobox } from "./CompanySearchCombobox";
import { SelectionPill } from "../../../components/ui/SelectionPill";

/**
 * The Off-limits panel: companies this mandate may not approach. Binding, with no show-anyway toggle — barred companies
 * drop from every read and count. Entries are picked from the universe (the bar is by account id), never typed.
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
    <div className="flex flex-col gap-3">
      <p className="font-sans text-[12px] leading-relaxed text-u-text3">
        Companies added here will be completely excluded from your active search results.
      </p>

      <CompanySearchCombobox
        listId="off-limits-search"
        excludedIds={excludedIds}
        onPick={add}
      />

      <div>
        <div className="mb-2 font-sans text-[10px] font-semibold tracking-[1px] text-u-text3">
          EXCLUDED ({companies.length})
        </div>
        {companies.length === 0 ? (
          <p className="font-sans text-[12px] text-u-text3">Nothing excluded yet.</p>
        ) : (
          <div className="flex flex-wrap gap-[6px]">
            {companies.map((company) => (
              <SelectionPill
                key={company.apolloAccountId}
                label={company.companyName}
                tone="accent"
                onRemove={() => remove(company.apolloAccountId)}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
