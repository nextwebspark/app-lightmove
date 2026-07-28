import { Drawer } from "../../../components/ui";
import { CompanyLogo } from "../../strategy/components/CompanyLogo";
import type { SourcedCompany } from "../api/types";
import { formatUsdCompact } from "../lib/format";
import { TIER_META } from "../lib/tierMeta";

/**
 * The right slide-over a company card opens: every field CoreSignal returned for the company —
 * description, scale, HQ, links. Pure props; the poll response already carries all of it, so this
 * fetches nothing.
 */
export function CompanyDetailDrawer({
  company,
  onClose,
}: {
  company: SourcedCompany | null;
  onClose: () => void;
}) {
  if (!company) return null;
  const tier = TIER_META[company.matchTier];

  const facts: { label: string; value: string | null }[] = [
    { label: "Sector", value: company.industry },
    { label: "Employees", value: company.employeesCount?.toLocaleString("en-US") ?? company.sizeRange },
    { label: "Size range", value: company.sizeRange },
    { label: "Revenue band", value: company.revenueRange },
    { label: "Annual revenue", value: formatUsdCompact(company.revenueAnnualUsd) },
    { label: "Headquarters", value: company.location },
    { label: "Country", value: company.country },
    { label: "Founded", value: company.foundedYear ? String(company.foundedYear) : null },
    { label: "CoreSignal id", value: String(company.coresignalId) },
  ];

  return (
    <Drawer open onClose={onClose}>
      <div className="relative border-b border-line-soft px-5 pb-4 pt-[18px]">
        <button
          type="button"
          onClick={onClose}
          aria-label="Close"
          className="absolute right-3.5 top-3.5 rounded-md p-1.5 text-text3 hover:bg-panel2 hover:text-text"
        >
          ✕
        </button>
        <div className="flex items-start gap-3 pr-8">
          <CompanyLogo name={company.name} logo={company.logoUrl} size={44} />
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-sans text-[16px] font-semibold leading-[1.25] text-text">
                {company.name}
              </span>
              <span
                className={`inline-flex flex-none items-center rounded-[5px] px-[7px] py-[2px] font-mono text-[9.5px] font-bold uppercase tracking-[0.06em] ${tier.className}`}
              >
                {tier.label}
              </span>
            </div>
            <div className="mt-1 font-mono text-[11.5px] text-text3">
              {company.location || "—"} · {company.industry ?? "—"}
            </div>
          </div>
        </div>
        <div className="mt-3 flex gap-2">
          {company.website && (
            <a
              href={company.website}
              target="_blank"
              rel="noreferrer"
              className="rounded-[7px] border border-line px-2.5 py-1 font-sans text-[12px] font-medium text-text2 hover:border-text3 hover:text-text"
            >
              Website ↗
            </a>
          )}
          {company.linkedinUrl && (
            <a
              href={company.linkedinUrl}
              target="_blank"
              rel="noreferrer"
              className="rounded-[7px] border border-line px-2.5 py-1 font-sans text-[12px] font-medium text-text2 hover:border-text3 hover:text-text"
            >
              LinkedIn ↗
            </a>
          )}
        </div>
      </div>

      <div className="flex-1 overflow-y-auto px-5 py-4">
        {company.description && (
          <>
            <div className="mb-2 font-mono text-[10px] font-semibold uppercase tracking-[0.1em] text-text3">
              About
            </div>
            <p className="mb-5 whitespace-pre-line font-sans text-[13px] leading-[1.55] text-text2">
              {company.description}
            </p>
          </>
        )}
        <div className="mb-2 font-mono text-[10px] font-semibold uppercase tracking-[0.1em] text-text3">
          Company facts
        </div>
        <dl className="flex flex-col gap-[7px] font-mono text-[12.5px]">
          {facts.map((fact) => (
            <div key={fact.label} className="flex items-baseline gap-3">
              <dt className="w-[120px] flex-none text-text3">{fact.label}</dt>
              <dd className="min-w-0 flex-1 text-text">{fact.value || "—"}</dd>
            </div>
          ))}
        </dl>
      </div>
    </Drawer>
  );
}
