import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import { SelectionCheckbox } from "../../../components/ui/SelectionCheckbox";
import { cn } from "../../../lib/cn";
import { formatNumber } from "../../../lib/format";
import type { TriageCompanyStatus } from "../../triage/api/types";
import { TRIAGE_STAGES } from "../../triage/lib/triageStages";
import type { AssistantProposal, ProposalOutcome, ProposedCompany } from "../api/types";

const ACCEPT_LABELS: Record<TriageCompanyStatus, string> = {
  inUniverse: "Universe",
  shortlisted: "Shortlist",
  declined: "Decline",
};

/**
 * The companies an answer proposed, each with a tick box, and a button per stage to file the ticked
 * ones. It writes nothing itself; once filed it shows the outcome instead of the buttons.
 */
export function AssistantProposalCard({
  proposal,
  outcome,
  filing,
  onAccept,
}: {
  proposal: AssistantProposal;
  outcome: ProposalOutcome | null;
  filing: boolean;
  onAccept: (apolloAccountIds: string[], status: TriageCompanyStatus) => void;
}) {
  const [ticked, setTicked] = useState<string[]>(() =>
    proposal.companies.map((company) => company.apolloAccountId),
  );

  if (outcome) {
    return (
      <div className="flex gap-2.5 rounded-[9px] border border-line-soft bg-panel2 px-3 py-2.5">
        <Icon d={ICONS.check} size={13} className="mt-0.5 flex-none text-green" />
        <p className="font-sans text-[11.5px] leading-[1.5] text-text2">{outcomeLine(outcome)}</p>
      </div>
    );
  }

  if (proposal.companies.length === 0) {
    return null;
  }

  const toggle = (id: string) =>
    setTicked((current) =>
      current.includes(id) ? current.filter((held) => held !== id) : [...current, id],
    );

  return (
    <div className="overflow-hidden rounded-[11px] border border-ai-line bg-panel shadow-panel">
      <div className="border-b border-line-soft bg-ai-soft px-3 py-2.5">
        <p className="font-sans text-[12.5px] font-semibold text-text">{proposal.title}</p>
      </div>

      <ul className="max-h-[258px] overflow-y-auto">
        {proposal.companies.map((company) => {
          const on = ticked.includes(company.apolloAccountId);
          return (
            <li
              key={company.apolloAccountId}
              className={cn(
                "flex items-center gap-2.5 border-b border-line-soft px-3 py-2.5",
                on && "bg-ai-soft",
              )}
            >
              <SelectionCheckbox
                checked={on}
                label={`Include ${company.companyName}`}
                onChange={() => toggle(company.apolloAccountId)}
              />
              <CompanyLogo name={company.companyName} logo={company.logoUrl} size={22} />
              <div className="min-w-0 flex-1">
                <p
                  className={cn(
                    "truncate font-sans text-[12.5px] font-medium text-text",
                    !on && "opacity-55",
                  )}
                >
                  {company.companyName}
                </p>
                <p className="truncate font-mono text-[10.5px] text-text3">{meta(company)}</p>
              </div>
            </li>
          );
        })}
      </ul>

      <div className="border-t border-line-soft bg-panel2 px-3 py-2.5">
        <p className="mb-[7px] font-mono text-[11px] text-text3">{acceptCountLabel(ticked.length)}</p>
        <div className="flex flex-wrap items-center gap-1.5">
          {TRIAGE_STAGES.map((stage) => (
            <button
              key={stage.status}
              type="button"
              disabled={filing || ticked.length === 0}
              onClick={() => onAccept(ticked, stage.status)}
              title={`File the selected companies as ${stage.label}`}
              className={cn(
                "inline-flex items-center gap-1.5 rounded-md border border-line bg-panel px-2.5 py-1.5 font-sans text-[11.5px] font-medium transition disabled:opacity-40",
                stage.status === "declined"
                  ? "text-red hover:border-red"
                  : "text-text2 hover:border-ai hover:text-text",
              )}
            >
              <Icon d={stage.icon} size={12} />
              {ACCEPT_LABELS[stage.status]}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

/** One sentence for what was filed, shared with the toast so the two never drift apart. */
export function outcomeLine(outcome: ProposalOutcome): string {
  const stage = TRIAGE_STAGES.find((one) => one.status === (outcome.status ?? "inUniverse"));
  const filed = `${outcome.added} ${outcome.added === 1 ? "company" : "companies"} filed to ${stage?.label ?? "In universe"}`;
  return outcome.skipped > 0 ? `${filed}, ${outcome.skipped} already in this mandate` : filed;
}

/** `!= null` because zero is a real headcount. */
function meta(company: ProposedCompany): string {
  const staff = company.employees != null ? `${formatNumber(company.employees)} staff` : "unknown";
  return [company.country, staff].filter(Boolean).join(" · ");
}

function acceptCountLabel(count: number): string {
  return count === 0 ? "Nothing selected" : `File ${count} ${count === 1 ? "company" : "companies"} as`;
}
