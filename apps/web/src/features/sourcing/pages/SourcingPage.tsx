import { useInfiniteQuery } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { Link, useOutletContext } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { EmptyState, Spinner } from "../../../components/ui";
import * as sourcingApi from "../api/sourcingApi";
import type { AppliedFilters, CompanyResult, MatchTier } from "../api/types";

const PAGE_SIZE = 25;

/** How each scope bucket a company matched through reads on the card badge. */
const TIER_META: Record<MatchTier, { label: string; className: string }> = {
  TARGET: { label: "Target", className: "text-green bg-green-dim" },
  DIRECT: { label: "Direct", className: "text-sky bg-sky-dim" },
  ADJACENT: { label: "Adjacent", className: "text-amber bg-amber-dim" },
  INFERRED: { label: "AI Inferred", className: "text-text3 bg-line-soft" },
};

const CHECK_ICON = "m5 13 4 4L19 7";

/**
 * The scope categories actually in play for this query, each paired with this company's own value.
 * Every row here is guaranteed met — a company only appears in the results because it satisfies all of
 * them — so this isn't a per-company fit score, just which of the Strategy's criteria produced this match.
 */
function criteriaRowsFor(company: CompanyResult, applied: AppliedFilters): { label: string; value: string }[] {
  const rows: { label: string; value: string }[] = [];
  if (applied.sector) rows.push({ label: "Sector", value: company.sector ?? "—" });
  if (applied.employee) rows.push({ label: "Employees", value: company.employeeRange ?? "—" });
  if (applied.revenue) rows.push({ label: "Revenue", value: company.revenueRange ?? "—" });
  if (applied.geography) rows.push({ label: "Region", value: company.location || "—" });
  return rows;
}

/** Placeholder triage actions — no candidate/pipeline tables exist yet to back them. */
const PLACEHOLDER_ACTIONS = [
  { label: "Comment", d: "M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2Z" },
  { label: "Add to universe", d: "m5 13 4 4L19 7" },
  { label: "Shortlist", d: "M12 2l3.09 6.26L22 9.27l-5 4.87L18.18 21 12 17.77 5.82 21 7 14.14l-5-4.87 6.91-1.01L12 2Z" },
] as const;

/**
 * The companies matching this project's saved Strategy scope (Project.dc.html, Sourcing screen) — a
 * plain filtered list for now. No fit score, tiers, or triage actions: those read from candidate/
 * pipeline tables that don't exist yet.
 */
export function SourcingPage() {
  const { project } = useOutletContext<ProjectOutletContext>();
  const [view, setView] = useState<"card" | "list">("card");
  const sentinelRef = useRef<HTMLDivElement>(null);

  const { data, isPending, fetchNextPage, hasNextPage, isFetchingNextPage } = useInfiniteQuery({
    queryKey: sourcingApi.SOURCING_KEY(project.id, PAGE_SIZE),
    queryFn: ({ pageParam }) => sourcingApi.getSourcingCompanies(project.id, pageParam, PAGE_SIZE),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => {
      const nextPage = lastPage.page + 1;
      return nextPage * lastPage.size < lastPage.totalCount ? nextPage : undefined;
    },
  });

  useEffect(() => {
    const sentinel = sentinelRef.current;
    if (!sentinel) {
      return;
    }
    const observer = new IntersectionObserver((entries) => {
      if (entries[0]?.isIntersecting && hasNextPage && !isFetchingNextPage) {
        fetchNextPage();
      }
    });
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [fetchNextPage, hasNextPage, isFetchingNextPage]);

  if (isPending) {
    return (
      <div className="flex justify-center pt-24">
        <Spinner />
      </div>
    );
  }

  const pages = data?.pages ?? [];
  const totalCount = pages[0]?.totalCount ?? 0;
  const companies = pages.flatMap((p) => p.companies);
  const appliedFilters = pages[0]?.appliedFilters ?? {
    sector: false,
    employee: false,
    revenue: false,
    geography: false,
  };

  return (
    <div className="animate-fade-up">
      <div className="mb-5 flex items-start justify-between gap-4">
        <div>
          <div className="font-sans text-[19px] font-semibold leading-[1.2]">Sourcing</div>
          <p className="mt-1 max-w-[640px] text-[13px] text-text2">
            Companies matching Strategy's live criteria — the single source of truth. Criteria changes
            apply to these results going forward.
          </p>
        </div>
        <div className="flex flex-none items-center gap-2">
          <Link
            to={`/projects/${project.id}/strategy`}
            className="flex items-center gap-[6px] rounded-[8px] border border-line px-3 py-[6px] font-sans text-[12.5px] font-medium text-text2 hover:border-text3 hover:text-text"
          >
            <Icon d={ICONS.strategy} size={14} />
            Edit criteria in Strategy
          </Link>
          <span className="flex overflow-hidden rounded-[8px] border border-line">
            <button
              type="button"
              title="Card view"
              onClick={() => setView("card")}
              className={`flex px-[10px] py-[6px] ${view === "card" ? "bg-panel text-amber" : "text-text3"}`}
            >
              <Icon d={ICONS.allProjects} size={15} />
            </button>
            <button
              type="button"
              title="List view"
              onClick={() => setView("list")}
              className={`flex px-[10px] py-[6px] ${view === "list" ? "bg-panel text-amber" : "text-text3"}`}
            >
              <Icon d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01" size={15} />
            </button>
          </span>
        </div>
      </div>

      {totalCount === 0 ? (
        <EmptyState
          icon={<Icon d={ICONS.sourcing} size={22} />}
          title="No companies match yet"
          body="Set a Sector or Company Size filter in Strategy to start sourcing companies for this mandate."
        >
          <Link
            to={`/projects/${project.id}/strategy`}
            className="rounded-[8px] border border-amber-btn bg-amber-btn px-[13px] py-[7px] font-sans text-[13px] font-semibold text-[#141414] hover:brightness-105"
          >
            Go to Strategy
          </Link>
        </EmptyState>
      ) : (
        <>
          <div className="mb-3 font-sans text-[13px] text-text">
            <b className="text-amber">{totalCount.toLocaleString("en-US")}</b> companies match the
            current criteria
          </div>

          {view === "card" ? (
            <div className="grid grid-cols-[repeat(auto-fill,minmax(360px,1fr))] gap-[14px]">
              {companies.map((company) => {
                const tier = TIER_META[company.matchTier];
                const criteriaRows = criteriaRowsFor(company, appliedFilters);
                return (
                  <div
                    key={company.id}
                    className="rounded-[10px] border border-line-soft bg-panel2 p-[18px] hover:border-line"
                  >
                    <div className="mb-3.5 flex items-start gap-3">
                      <div className="flex size-10 flex-none items-center justify-center rounded-[8px] border border-line bg-panel font-mono text-[13px] font-semibold text-text2">
                        {initialsOf(company.name)}
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="font-sans text-[15px] font-semibold leading-[1.3] text-text">
                            {company.name}
                          </span>
                          <span
                            className={`inline-flex flex-none items-center rounded-[5px] px-[7px] py-[2px] font-mono text-[9.5px] font-bold uppercase tracking-[0.06em] ${tier.className}`}
                          >
                            {tier.label}
                          </span>
                        </div>
                        <div className="mt-1 font-mono text-[11.5px] text-text3">
                          {company.location || "—"} · {company.sector ?? "—"}
                        </div>
                      </div>
                    </div>
                    <div className="grid grid-cols-[minmax(0,1.35fr)_minmax(0,1fr)] gap-5 border-t border-line-soft pb-3 pt-3.5">
                      <div className="min-w-0">
                        <div className="mb-[9px] font-mono text-[10px] font-semibold uppercase tracking-[0.1em] text-text3">
                          {criteriaRows.length} of {criteriaRows.length} met
                        </div>
                        <div className="flex flex-col gap-[5px] font-mono text-[12.5px]">
                          {criteriaRows.map((row) => (
                            <div key={row.label} className="flex items-center gap-2">
                              <Icon d={CHECK_ICON} size={13} className="flex-none text-sky" />
                              <span className="w-[70px] flex-none text-text3">{row.label}</span>
                              <span className="min-w-0 flex-1 truncate text-sky" title={row.value}>
                                {row.value}
                              </span>
                            </div>
                          ))}
                        </div>
                      </div>
                      <div className="min-w-0">
                        <div className="mb-[9px] font-mono text-[10px] font-semibold uppercase tracking-[0.1em] text-text3">
                          Scale Snapshot
                        </div>
                        <div className="flex flex-col gap-[5px] font-mono text-[12.5px]">
                          {[
                            { label: "Revenue", value: company.revenueRange },
                            { label: "Employees", value: company.employeeRange },
                            { label: "Region", value: company.location },
                            { label: "Sector", value: company.sector },
                          ].map((row) => (
                            <div key={row.label} className="flex items-center gap-2">
                              <span className="w-[70px] flex-none text-text3">{row.label}</span>
                              <span className="text-text">{row.value || "—"}</span>
                            </div>
                          ))}
                        </div>
                      </div>
                    </div>
                    <div className="flex items-center gap-1 border-t border-line-soft pt-2.5">
                      {PLACEHOLDER_ACTIONS.map((action) => (
                        <button
                          key={action.label}
                          type="button"
                          disabled
                          title="Not available yet"
                          aria-label={action.label}
                          className="flex size-[30px] items-center justify-center rounded-[7px] text-text3 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          <Icon d={action.d} size={15} />
                        </button>
                      ))}
                    </div>
                  </div>
                );
              })}
            </div>
          ) : (
            <div className="overflow-auto rounded-[10px] border border-line-soft">
              <table className="w-full border-collapse">
                <thead>
                  <tr>
                    {["Company", "Sector", "Employees", "Revenue", "Location"].map((label) => (
                      <th
                        key={label}
                        className="whitespace-nowrap border-b border-line px-[14px] py-[10px] text-left font-mono text-[10.5px] font-semibold uppercase tracking-[0.1em] text-text3"
                      >
                        {label}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {companies.map((company) => (
                    <tr key={company.id}>
                      <td className="whitespace-nowrap border-b border-line-soft px-[14px] py-[10px] font-sans text-[13px] font-semibold text-text">
                        {company.name}
                      </td>
                      <td className="whitespace-nowrap border-b border-line-soft px-[14px] py-[10px] font-mono text-[12px] text-text2">
                        {company.sector ?? "—"}
                      </td>
                      <td className="whitespace-nowrap border-b border-line-soft px-[14px] py-[10px] font-mono text-[12px] text-text2">
                        {company.employeeRange ?? "—"}
                      </td>
                      <td className="whitespace-nowrap border-b border-line-soft px-[14px] py-[10px] font-mono text-[12px] text-text2">
                        {company.revenueRange ?? "—"}
                      </td>
                      <td className="whitespace-nowrap border-b border-line-soft px-[14px] py-[10px] font-mono text-[12px] text-text2">
                        {company.location || "—"}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div ref={sentinelRef} className="flex justify-center pt-4">
            {isFetchingNextPage && <Spinner />}
          </div>
        </>
      )}
    </div>
  );
}

/** "Alpha Retail Group" → "AR" — the card's avatar tile, first letters of the first two words. */
function initialsOf(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((word) => word[0]?.toUpperCase() ?? "")
    .join("");
}
