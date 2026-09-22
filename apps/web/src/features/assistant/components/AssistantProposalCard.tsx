import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { SelectionCheckbox } from "../../../components/ui/SelectionCheckbox";
import { cn } from "../../../lib/cn";
import { formatNumber, initials } from "../../../lib/format";
import type { TriageCompanyStatus } from "../../triage/api/types";
import { TRIAGE_STAGES } from "../../triage/lib/triageStages";
import type { AssistantProposal, ProposedCompany } from "../api/types";

/** Where a row's data came from, and what that is worth — the mockup's own three colours. */
const PROVENANCE: Record<
  ProposedCompany["origin"],
  { label: string; className: string; title: string }
> = {
  UNIVERSE: {
    label: "universe",
    className: "text-green bg-green-dim",
    title: "Already in your universe — nothing was spent",
  },
  RESEARCHED: {
    label: "bright data",
    className: "text-amber bg-amber-dim",
    title: "Bought from a vendor and kept, so it is never bought again",
  },
  WEB: {
    label: "web",
    className: "text-text3 bg-panel2",
    title: "Named by an open-web search and not verified against any dataset",
  },
};

/** How the sub-line counts what the card is made of, in the mockup's own words. */
const COMPOSITION: Record<ProposedCompany["origin"], string> = {
  UNIVERSE: "already held",
  RESEARCHED: "verified",
  WEB: "from the web",
};

/** The accept bar says the act, not the stage name: its caption reads "File 4 companies as". */
const ACCEPT_LABELS: Record<TriageCompanyStatus, string> = {
  inUniverse: "Universe",
  shortlisted: "Shortlist",
  declined: "Decline",
};

/**
 * Companies the assistant is offering, and what became of them.
 *
 * <p><b>It writes nothing.</b> Ticking is local, and accepting hands the caller the refs and the
 * stage — the same rule the position document's review panel was built on, and the reason the
 * proposing tool writes nothing either: an agent filing forty companies into a client's mandate
 * unprompted is the mistake this whole path exists to make impossible.
 *
 * <p>Provenance is on every row, always. Green is a company the firm already licenses, amber one a
 * vendor was paid for, grey a name off the open web that nobody verified — and the last of those is
 * the reason a badge is not decoration.
 */
export function AssistantProposalCard({
  proposal,
  running,
  filing,
  onAccept,
  onDismiss,
}: {
  proposal: AssistantProposal;
  /** The turn is still answering, which the server refuses to file against. */
  running: boolean;
  filing: boolean;
  onAccept: (refs: string[], status: TriageCompanyStatus) => void;
  onDismiss: () => void;
}) {
  // A row the assistant could not verify starts unticked: filing an unchecked company has to be
  // something somebody chose, not something they failed to undo.
  const [ticked, setTicked] = useState<string[]>(() =>
    proposal.companies.filter((company) => company.origin !== "WEB").map((company) => company.ref),
  );

  if (proposal.accepted) {
    return <ProposalOutcome accepted={proposal.accepted} />;
  }

  const toggle = (ref: string) =>
    setTicked((current) =>
      current.includes(ref) ? current.filter((held) => held !== ref) : [...current, ref],
    );

  return (
    <div className="overflow-hidden rounded-[11px] border border-ai-line bg-panel shadow-panel">
      <div className="border-b border-line-soft bg-ai-soft px-3 py-2.5">
        <p className="font-sans text-[12.5px] font-semibold text-text">{proposal.title}</p>
        <p className="mt-[3px] font-mono text-[11px] text-text3">{composition(proposal.companies)}</p>
      </div>

      <ul className="max-h-[258px] overflow-y-auto">
        {proposal.companies.map((company) => {
          const on = ticked.includes(company.ref);
          const provenance = PROVENANCE[company.origin];
          return (
            <li
              key={company.ref}
              className={cn(
                "flex items-center gap-2.5 border-b border-line-soft px-3 py-2.5",
                on && "bg-ai-soft",
              )}
            >
              <SelectionCheckbox
                checked={on}
                label={`Include ${company.companyName}`}
                onChange={() => toggle(company.ref)}
              />
              <span className="grid size-[22px] flex-none place-items-center rounded-[5px] bg-panel2 font-mono text-[9px] font-bold text-text3">
                {initials(company.companyName)}
              </span>
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
              <span
                title={provenance.title}
                className={cn(
                  "flex-none rounded px-1.5 py-0.5 font-mono text-[9px] font-semibold uppercase tracking-[0.04em]",
                  provenance.className,
                )}
              >
                {provenance.label}
              </span>
            </li>
          );
        })}
      </ul>

      <div className="border-t border-line-soft bg-panel2 px-3 py-2.5">
        <p className="mb-[7px] font-mono text-[11px] text-text3">
          {running ? "Waiting for the answer to finish" : acceptCountLabel(ticked.length)}
        </p>
        <div className="flex flex-wrap items-center gap-1.5">
          {TRIAGE_STAGES.map((stage) => (
            <button
              key={stage.status}
              type="button"
              disabled={running || filing || ticked.length === 0}
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
          <button
            type="button"
            onClick={onDismiss}
            disabled={filing}
            className="ms-auto border-none bg-transparent font-sans text-[11.5px] text-text3 transition hover:text-text disabled:opacity-40"
          >
            Dismiss
          </button>
        </div>
      </div>
    </div>
  );
}

/**
 * What a filed proposal reads as afterwards.
 *
 * <p>The card is never rewritten — the proposal is an immutable event and the acceptance is a second
 * one — so this is the outcome read alongside it, and it is what stops a reopened conversation
 * offering buttons for companies that are already on the grid.
 */
function ProposalOutcome({ accepted }: { accepted: NonNullable<AssistantProposal["accepted"]> }) {
  return (
    <div className="flex gap-2.5 rounded-[9px] border border-line-soft bg-panel2 px-3 py-2.5">
      <Icon d={ICONS.check} size={13} className="mt-0.5 flex-none text-green" />
      <p className="font-sans text-[11.5px] leading-[1.5] text-text2">{outcomeLine(accepted)}</p>
    </div>
  );
}

/** One sentence for what was filed, shared with the toast so the two never drift apart. */
export function outcomeLine(accepted: NonNullable<AssistantProposal["accepted"]>): string {
  const filed = `${accepted.added} ${accepted.added === 1 ? "company" : "companies"} filed to ${stageLabel(accepted.status)}`;
  return accepted.skipped > 0
    ? `${filed}, ${accepted.skipped} already in this mandate`
    : filed;
}

/** An accept that named no stage filed in universe, and the server stores that as an empty string. */
function stageLabel(status: string): string {
  // Named rather than TRIAGE_STAGES[0]: a reorder of that array must not quietly change what the
  // outcome line and the toast claim was done with somebody's companies.
  const wanted = status || "inUniverse";
  return TRIAGE_STAGES.find((stage) => stage.status === wanted)?.label ?? wanted;
}

function composition(companies: ProposedCompany[]): string {
  const counted = (Object.keys(COMPOSITION) as ProposedCompany["origin"][])
    .map((origin) => ({ origin, count: companies.filter((one) => one.origin === origin).length }))
    .filter((group) => group.count > 0)
    .map((group) => `${group.count} ${COMPOSITION[group.origin]}`);
  return counted.join(" · ");
}

/**
 * `!= null`, not a truthiness check: **zero is a legitimate headcount** and the repo already says so
 * — {@code CaptureCompanyRequest} calls it "a headcount, not a population" and names a holding
 * company and a newly incorporated entity. A falsy test renders those as unmeasured.
 */
function meta(company: ProposedCompany): string {
  const staff =
    company.employees != null ? `${formatNumber(company.employees)} staff` : "unknown";
  return [company.country, staff].filter(Boolean).join(" · ");
}

function acceptCountLabel(count: number): string {
  return count === 0 ? "Nothing selected" : `File ${count} ${count === 1 ? "company" : "companies"} as`;
}
