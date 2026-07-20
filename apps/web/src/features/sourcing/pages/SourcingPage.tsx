import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useOutletContext } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { EmptyState, Spinner } from "../../../components/ui";
import * as sourcingApi from "../api/sourcingApi";

const PAGE_SIZE = 25;

/**
 * The companies matching this project's saved Strategy scope (Project.dc.html, Sourcing screen) — a
 * plain filtered list for now. No fit score, tiers, or triage actions: those read from candidate/
 * pipeline tables that don't exist yet.
 */
export function SourcingPage() {
  const { project } = useOutletContext<ProjectOutletContext>();
  const [page, setPage] = useState(0);

  const { data, isPending } = useQuery({
    queryKey: sourcingApi.SOURCING_KEY(project.id, page, PAGE_SIZE),
    queryFn: () => sourcingApi.getSourcingCompanies(project.id, page, PAGE_SIZE),
    placeholderData: keepPreviousData,
  });

  if (isPending) {
    return (
      <div className="flex justify-center pt-24">
        <Spinner />
      </div>
    );
  }

  const totalCount = data?.totalCount ?? 0;
  const companies = data?.companies ?? [];
  const totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE));

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
        <Link
          to={`/projects/${project.id}/strategy`}
          className="flex flex-none items-center gap-[6px] rounded-[8px] border border-line px-3 py-[6px] font-sans text-[12.5px] font-medium text-text2 hover:border-text3 hover:text-text"
        >
          <Icon d={ICONS.strategy} size={14} />
          Edit criteria in Strategy
        </Link>
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

          <div className="mt-3 flex items-center justify-between gap-3">
            <span className="font-mono text-[11px] text-text3">
              Page {page + 1} of {totalPages}
            </span>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setPage((current) => Math.max(0, current - 1))}
                disabled={page === 0}
                className="rounded-[7px] border border-line px-[11px] py-[5px] font-sans text-[12.5px] font-medium text-text2 disabled:cursor-not-allowed disabled:opacity-40 hover:enabled:border-text3 hover:enabled:text-text"
              >
                Previous
              </button>
              <button
                type="button"
                onClick={() => setPage((current) => current + 1)}
                disabled={page + 1 >= totalPages}
                className="rounded-[7px] border border-line px-[11px] py-[5px] font-sans text-[12.5px] font-medium text-text2 disabled:cursor-not-allowed disabled:opacity-40 hover:enabled:border-text3 hover:enabled:text-text"
              >
                Next
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
