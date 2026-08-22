import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { Link, useOutletContext } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { EmptyState } from "../../../components/ui";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import { PaginationBar } from "../../../components/ui/PaginationBar";
import { useToast } from "../../../components/ui/Toast";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { PAGE_SIZE } from "../../../lib/paging";
import * as triageApi from "../api/triageApi";
import type { TriageCompany, TriageCompanyStatus } from "../api/types";

/**
 * The three stages, with the empty copy each one needs.
 *
 * <p>An empty stage is not one situation. "In universe" empty means nobody has searched yet and the
 * next move is Strategy; "Shortlisted" empty means the working set has not been promoted from; and
 * "Declined" empty is a stage a mandate can legitimately stay at forever. One shared sentence —
 * "Companies added from Strategy land here" — was wrong on two of the three.
 */
const TABS: {
  status: TriageCompanyStatus;
  label: string;
  icon: string;
  emptyTitle: string;
  emptyBody: string;
}[] = [
  {
    status: "inUniverse",
    label: "In universe",
    icon: ICONS.globe,
    emptyTitle: "No companies in the universe yet",
    emptyBody: "Filter the market on Strategy, then add the companies worth a closer look.",
  },
  {
    status: "shortlisted",
    label: "Shortlisted",
    icon: ICONS.star,
    emptyTitle: "Nothing shortlisted yet",
    emptyBody: "Promote a company from In universe once it is worth mapping people at.",
  },
  {
    status: "declined",
    label: "Declined",
    icon: ICONS.close,
    emptyTitle: "Nothing declined",
    emptyBody:
      "Companies you rule out land here, and stay ruled out the next time someone adds in bulk.",
  },
];

/**
 * The mandate's triaged companies, one stage at a time — In universe, Shortlisted, Declined, with
 * their counts.
 *
 * <p>A mandate nobody has triaged shows an empty screen rather than the market. That is the change
 * this screen went through: discovery moved to Strategy, and what remains here is the record of
 * decisions, which starts empty and stays that way until someone makes one.
 */
export function TriagePage() {
  const { project } = useOutletContext<ProjectOutletContext>();
  const queryClient = useQueryClient();
  const toast = useToast();

  const [status, setStatus] = useState<TriageCompanyStatus>("inUniverse");
  const [page, setPage] = useState(0);

  useEffect(() => setPage(0), [status]);

  const triaged = useQuery({
    queryKey: triageApi.TRIAGE_KEY(project.id, status, page, PAGE_SIZE),
    queryFn: ({ signal }) => triageApi.getTriageCompanies(project.id, status, page, PAGE_SIZE, signal),
    placeholderData: keepPreviousData,
  });

  const triage = useMutation({
    mutationFn: ({ id, next }: { id: string; next: TriageCompanyStatus }) =>
      triageApi.updateTriageCompany(project.id, id, { status: next }),
    onSuccess: () => void queryClient.invalidateQueries({
      queryKey: triageApi.TRIAGE_KEY_PREFIX(project.id),
    }),
    onError: (error) => toast(messageFor(error)),
  });

  const activeTab = TABS.find((tab) => tab.status === status)!;
  const counts = triaged.data?.counts;
  const companies = triaged.data?.companies ?? [];
  const lastPage = Math.max(0, Math.ceil((triaged.data?.totalCount ?? 0) / PAGE_SIZE) - 1);

  return (
    <div className="animate-fade-up">
      <div className="mb-5">
        <h1 className="font-sans text-[19px]/[1.2] font-semibold">Triage</h1>
        <p className="mt-1 max-w-[640px] text-[13px] text-text2">
          The companies this mandate has taken a position on. Add them from{" "}
          <Link to={`/projects/${project.id}/strategy`} className="text-amber hover:underline">
            Strategy
          </Link>
          ; triage them here.
        </p>
      </div>

      <div className="mb-4 flex items-center gap-1.5">
        {TABS.map((tab) => (
          <button
            key={tab.status}
            type="button"
            onClick={() => setStatus(tab.status)}
            aria-pressed={status === tab.status}
            className={cn(
              "inline-flex items-center gap-2 rounded-full border px-[11px] py-[5px] font-mono text-xs font-medium transition hover:text-text",
              status === tab.status ? "border-amber bg-amber-dim text-amber" : "border-line text-text2",
            )}
          >
            {tab.label}
            <span className="font-mono text-[10.5px] text-text3">
              {counts ? counts[tab.status] : "—"}
            </span>
          </button>
        ))}
      </div>

      {/* A refused read is not an empty universe: branch on the error before the count, or a 403
          tells the reader this mandate has triaged nothing. */}
      {triaged.isError ? (
        <EmptyState
          icon={<Icon d={ICONS.warning} size={22} />}
          title="That list could not be loaded"
          body="Refresh the page, or check you still have access to this mandate."
        />
      ) : companies.length === 0 && !triaged.isFetching ? (
        <EmptyState
          icon={<Icon d={activeTab.icon} size={22} />}
          title={activeTab.emptyTitle}
          body={activeTab.emptyBody}
        />
      ) : (
        <div className="overflow-hidden rounded-[10px] border border-line">
          {companies.map((company) => (
            <TriageRow
              key={company.id}
              company={company}
              onTriage={(next) => triage.mutate({ id: company.id, next })}
            />
          ))}
        </div>
      )}

      {lastPage > 0 && (
        <div className="mt-3">
          <PaginationBar
            page={page}
            size={PAGE_SIZE}
            totalCount={triaged.data?.totalCount ?? 0}
            onPage={setPage}
          />
        </div>
      )}
    </div>
  );
}

function TriageRow({
  company,
  onTriage,
}: {
  company: TriageCompany;
  onTriage: (status: TriageCompanyStatus) => void;
}) {
  return (
    <div
      className={cn(
        "flex items-center gap-3 border-b border-line-soft px-4 py-2.5 transition last:border-b-0 hover:bg-panel2",
        company.status === "declined" && "opacity-60",
      )}
    >
      <CompanyLogo name={company.companyName} logo={company.logoUrl} size={26} />
      <div className="min-w-0 flex-1">
        <div className="truncate font-sans text-[13px] font-medium text-text">
          {company.companyName}
        </div>
        <div className="truncate font-mono text-[11.5px] text-text3">
          {[company.companyCity, company.companyCountry, company.industry].filter(Boolean).join(" · ")}
        </div>
      </div>
      {company.numEmployees !== null && (
        <span className="flex-none font-mono text-[11.5px] text-text3">
          {company.numEmployees.toLocaleString()} staff
        </span>
      )}
      <div className="flex flex-none items-center gap-1.5">
        {company.status !== "shortlisted" && (
          <TriageButton
            label="Shortlist"
            path={ICONS.star}
            onClick={() => onTriage("shortlisted")}
          />
        )}
        {company.status !== "declined" && (
          <TriageButton
            label="Decline"
            path={ICONS.close}
            onClick={() => onTriage("declined")}
          />
        )}
        {company.status !== "inUniverse" && (
          <TriageButton
            label="Back to universe"
            path="M3 12a9 9 0 1 0 3-6.7L3 8"
            onClick={() => onTriage("inUniverse")}
          />
        )}
      </div>
    </div>
  );
}

function TriageButton({
  label,
  path,
  onClick,
}: {
  label: string;
  path: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      title={label}
      aria-label={label}
      onClick={onClick}
      className="grid size-7 place-items-center rounded-[6px] text-text3 transition hover:bg-panel hover:text-text"
    >
      <Icon d={path} size={14} />
    </button>
  );
}
