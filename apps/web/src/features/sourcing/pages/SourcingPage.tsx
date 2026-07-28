import { useMutation, useQuery, useQueryClient, useIsMutating } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { Link, useOutletContext } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { EmptyState, Skeleton, Spinner } from "../../../components/ui";
import * as strategyApi from "../../strategy/api/strategyApi";
import { CompanyLogo } from "../../strategy/components/CompanyLogo";
import * as sourcingApi from "../api/sourcingApi";
import type { SourcedCompany, SourcingRun } from "../api/types";
import { CompanyDetailDrawer } from "../components/CompanyDetailDrawer";
import { formatUsdCompact } from "../lib/format";
import { TIER_META } from "../lib/tierMeta";

const POLL_INTERVAL_MS = 1500;

function isActive(run: SourcingRun | null | undefined): boolean {
  return run?.status === "PENDING" || run?.status === "SEARCHING" || run?.status === "COLLECTING";
}

function hasMoreToCollect(run: SourcingRun): boolean {
  return run.requestedCount < run.searchedCount;
}

/** Placeholder triage actions — no candidate/pipeline tables exist yet to back them. */
const PLACEHOLDER_ACTIONS = [
  { label: "Comment", d: "M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2Z" },
  { label: "Add to universe", d: "m5 13 4 4L19 7" },
  { label: "Shortlist", d: "M12 2l3.09 6.26L22 9.27l-5 4.87L18.18 21 12 17.77 5.82 21 7 14.14l-5-4.87 6.91-1.01L12 2Z" },
] as const;

const LINKEDIN_ICON = "M16 8a6 6 0 0 1 6 6v7h-4v-7a2 2 0 0 0-4 0v7h-4v-7a6 6 0 0 1 6-6ZM2 9h4v12H2zM4 6a2 2 0 1 0 0-4 2 2 0 0 0 0 4Z";
const GLOBE_ICON = "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20ZM2 12h20M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10Z";

/**
 * The Sourcing screen, POC-rewired to live CoreSignal data: a background run searches (fixing the
 * revenue-desc order up-front) and collects company profiles in parallel; this page polls the run
 * and streams results in as they land — collected cards fill skeleton slots, order never shifts.
 * Scrolling to the sentinel pays for the next batch. The old local-universe endpoint still exists
 * server-side; this screen no longer reads it.
 */
export function SourcingPage() {
  const { project } = useOutletContext<ProjectOutletContext>();
  const [view, setView] = useState<"card" | "list">("card");
  const [selected, setSelected] = useState<SourcedCompany | null>(null);
  const sentinelRef = useRef<HTMLDivElement>(null);
  const queryClient = useQueryClient();

  // A Strategy scope save is in flight. Hold the poll until it commits so a run is never started
  // against a scope that is still being written (same guard the pre-POC page had).
  const isStrategySaving = useIsMutating({ mutationKey: strategyApi.STRATEGY_WRITE_KEY(project.id) }) > 0;

  const { data, isPending } = useQuery({
    queryKey: sourcingApi.RUN_KEY(project.id),
    queryFn: () => sourcingApi.getCurrentRun(project.id),
    enabled: !isStrategySaving,
    // Poll only while the run is moving; a READY/FAILED run sits still until acted on.
    refetchInterval: (query) => (isActive(query.state.data?.run) ? POLL_INTERVAL_MS : false),
  });
  const run = data?.run ?? null;

  const start = useMutation({
    mutationFn: () => sourcingApi.startRun(project.id),
    onSuccess: (response) => queryClient.setQueryData(sourcingApi.RUN_KEY(project.id), response),
  });
  const extend = useMutation({
    mutationFn: () => sourcingApi.extendRun(project.id),
    onSuccess: (response) => queryClient.setQueryData(sourcingApi.RUN_KEY(project.id), response),
  });

  // Auto-start: no run yet, or the stored run answers an older strategy. A FAILED run keeps its
  // matching hash, so it never auto-retries in a loop — retry there is an explicit button.
  const needsStart = data !== undefined && (run === null || (!run.criteriaMatchesStrategy && run.status !== "FAILED"));
  useEffect(() => {
    if (needsStart && !isStrategySaving && !start.isPending) {
      start.mutate();
    }
  }, [needsStart, isStrategySaving, start]);

  // Infinite scroll = paying for the next batch, so it only fires on a READY run with more to give.
  const canExtend = run?.status === "READY" && hasMoreToCollect(run) && !extend.isPending;
  useEffect(() => {
    const sentinel = sentinelRef.current;
    if (!sentinel || !canExtend) return;
    const observer = new IntersectionObserver((entries) => {
      if (entries[0]?.isIntersecting) extend.mutate();
    });
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [canExtend, extend]);

  if (isStrategySaving || isPending || (needsStart && !run)) {
    return (
      <div className="flex justify-center pt-24">
        <Spinner />
      </div>
    );
  }

  return (
    <div className="animate-fade-up">
      <div className="mb-5 flex items-start justify-between gap-4">
        <div>
          <div className="font-sans text-[19px] font-semibold leading-[1.2]">Sourcing</div>
          <p className="mt-1 max-w-[640px] text-[13px] text-text2">
            Companies sourced live from CoreSignal for Strategy's criteria, highest revenue first.
            Criteria changes start a fresh search.
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

      {run?.status === "FAILED" ? (
        <EmptyState
          icon={<Icon d={ICONS.sourcing} size={22} />}
          title="Sourcing failed"
          body={run.error ?? "The sourcing provider is unavailable."}
        >
          <button
            type="button"
            onClick={() => start.mutate()}
            disabled={start.isPending}
            className="rounded-[8px] border border-amber-btn bg-amber-btn px-[13px] py-[7px] font-sans text-[13px] font-semibold text-[#141414] hover:brightness-105 disabled:opacity-60"
          >
            Retry
          </button>
        </EmptyState>
      ) : run && run.status === "READY" && run.totalMatched === 0 ? (
        <EmptyState
          icon={<Icon d={ICONS.sourcing} size={22} />}
          title="No companies match yet"
          body="Set a Sector filter in Strategy to start sourcing companies for this mandate from CoreSignal."
        >
          <Link
            to={`/projects/${project.id}/strategy`}
            className="rounded-[8px] border border-amber-btn bg-amber-btn px-[13px] py-[7px] font-sans text-[13px] font-semibold text-[#141414] hover:brightness-105"
          >
            Go to Strategy
          </Link>
        </EmptyState>
      ) : run ? (
        <>
          <div className="mb-3 font-sans text-[13px] text-text" aria-live="polite">
            {run.status === "PENDING" || run.status === "SEARCHING" ? (
              "Searching CoreSignal…"
            ) : run.status === "COLLECTING" ? (
              `Collecting company profiles ${run.collectedCount} of ${run.requestedCount}…`
            ) : (
              <>
                <b className="text-amber">{run.collectedCount.toLocaleString("en-US")}</b> of{" "}
                <b className="text-amber">{run.totalMatched.toLocaleString("en-US")}</b> matching
                companies collected
              </>
            )}
          </div>

          {view === "card" ? (
            <div className="grid grid-cols-[repeat(auto-fill,minmax(360px,1fr))] gap-[14px]">
              {run.companies.map((company) => (
                <CompanyCard key={company.coresignalId} company={company} onOpen={() => setSelected(company)} />
              ))}
              {isActive(run) &&
                Array.from({ length: Math.max(run.requestedCount - run.collectedCount, 1) }).map(
                  (_, index) => <SkeletonCard key={`skeleton-${index}`} />,
                )}
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
                  {run.companies.map((company) => (
                    <tr
                      key={company.coresignalId}
                      onClick={() => setSelected(company)}
                      className="cursor-pointer hover:bg-panel2"
                    >
                      <td className="whitespace-nowrap border-b border-line-soft px-[14px] py-[10px] font-sans text-[13px] font-semibold text-text">
                        {company.name}
                      </td>
                      <td className="whitespace-nowrap border-b border-line-soft px-[14px] py-[10px] font-mono text-[12px] text-text2">
                        {company.industry ?? "—"}
                      </td>
                      <td className="whitespace-nowrap border-b border-line-soft px-[14px] py-[10px] font-mono text-[12px] text-text2">
                        {company.employeesCount?.toLocaleString("en-US") ?? company.sizeRange ?? "—"}
                      </td>
                      <td className="whitespace-nowrap border-b border-line-soft px-[14px] py-[10px] font-mono text-[12px] text-text2">
                        {company.revenueRange ?? formatUsdCompact(company.revenueAnnualUsd) ?? "—"}
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
            {extend.isPending && <Spinner />}
          </div>
        </>
      ) : null}

      <CompanyDetailDrawer company={selected} onClose={() => setSelected(null)} />
    </div>
  );
}

function CompanyCard({ company, onOpen }: { company: SourcedCompany; onOpen: () => void }) {
  const tier = TIER_META[company.matchTier];
  const snapshot = [
    { label: "Revenue", value: company.revenueRange ?? formatUsdCompact(company.revenueAnnualUsd) },
    { label: "Employees", value: company.employeesCount?.toLocaleString("en-US") ?? company.sizeRange },
    { label: "Region", value: company.location },
    { label: "Sector", value: company.industry },
  ];

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onOpen}
      onKeyDown={(event) => {
        if (event.key === "Enter" || event.key === " ") onOpen();
      }}
      className="cursor-pointer rounded-[10px] border border-line-soft bg-panel2 p-[18px] text-left hover:border-line"
    >
      <div className="mb-3.5 flex items-start gap-3">
        <CompanyLogo name={company.name} logo={company.logoUrl} size={40} />
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
            {company.location || "—"} · {company.industry ?? "—"}
          </div>
        </div>
      </div>
      <div className="border-t border-line-soft pb-3 pt-3.5">
        <div className="mb-[9px] font-mono text-[10px] font-semibold uppercase tracking-[0.1em] text-text3">
          Scale Snapshot
        </div>
        <div className="flex flex-col gap-[5px] font-mono text-[12.5px]">
          {snapshot.map((row) => (
            <div key={row.label} className="flex items-center gap-2">
              <span className="w-[70px] flex-none text-text3">{row.label}</span>
              <span className="min-w-0 flex-1 truncate text-text" title={row.value ?? undefined}>
                {row.value || "—"}
              </span>
            </div>
          ))}
        </div>
      </div>
      <div className="flex items-center gap-1 border-t border-line-soft pt-2.5">
        {company.website && (
          <a
            href={company.website}
            target="_blank"
            rel="noreferrer"
            title="Website"
            aria-label={`${company.name} website`}
            onClick={(event) => event.stopPropagation()}
            className="flex size-[30px] items-center justify-center rounded-[7px] text-text2 hover:bg-panel hover:text-text"
          >
            <Icon d={GLOBE_ICON} size={15} />
          </a>
        )}
        {company.linkedinUrl && (
          <a
            href={company.linkedinUrl}
            target="_blank"
            rel="noreferrer"
            title="LinkedIn"
            aria-label={`${company.name} LinkedIn`}
            onClick={(event) => event.stopPropagation()}
            className="flex size-[30px] items-center justify-center rounded-[7px] text-text2 hover:bg-panel hover:text-text"
          >
            <Icon d={LINKEDIN_ICON} size={15} />
          </a>
        )}
        <span className="flex-1" />
        {PLACEHOLDER_ACTIONS.map((action) => (
          <button
            key={action.label}
            type="button"
            disabled
            title="Not available yet"
            aria-label={action.label}
            onClick={(event) => event.stopPropagation()}
            className="flex size-[30px] items-center justify-center rounded-[7px] text-text3 disabled:cursor-not-allowed disabled:opacity-60"
          >
            <Icon d={action.d} size={15} />
          </button>
        ))}
      </div>
    </div>
  );
}

/** A collect still in flight — same footprint as a real card, so the grid never jumps on fill-in. */
function SkeletonCard() {
  return (
    <div data-testid="company-skeleton" className="rounded-[10px] border border-line-soft bg-panel2 p-[18px]">
      <div className="mb-3.5 flex items-start gap-3">
        <Skeleton className="size-10 flex-none" />
        <div className="min-w-0 flex-1">
          <Skeleton className="h-4 w-40" />
          <Skeleton className="mt-2 h-3 w-28" />
        </div>
      </div>
      <div className="flex flex-col gap-[7px] border-t border-line-soft pt-3.5">
        <Skeleton className="h-3 w-full" />
        <Skeleton className="h-3 w-3/4" />
        <Skeleton className="h-3 w-2/3" />
      </div>
    </div>
  );
}
