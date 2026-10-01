import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Link, useOutletContext } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { Button } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { formatInstantDate } from "../../../lib/format";
import { useDebouncedValue } from "../../../lib/useComboboxList";
import { useAuth } from "../../auth/AuthProvider";
import { canExecuteProjectWork } from "../../projects/lib/access";
import { ReportCandidateDrawer } from "../../reports/components/ReportCandidateDrawer";
import * as candidatesApi from "../api/candidatesApi";
import type { Candidate, CandidatePipelineStaffRow, CandidateStatus } from "../api/types";
import { AddFromPoolPicker } from "../components/AddFromPoolPicker";
import { CandidateAvatar } from "../components/CandidateAvatar";
import { TagPill } from "../components/pool/TagPill";
import { lastActivityOf, shortWhen } from "../lib/candidateActivity";
import { CANDIDATE_SOURCE_STYLES, CANDIDATE_STATUSES, candidateStatusStyle } from "../lib/candidateVocabulary";
import { useChangeCandidateStatus } from "../lib/useChangeCandidateStatus";
import { usePoolLookups } from "../lib/usePoolLookups";

const PAGE_SIZE = 50;

/**
 * The position's own Candidates page: one row per person mapped to it. The person is the workspace's;
 * the status and the Added line are this position's. A client seat reads Executive, Status and Added
 * only — the tags, the other positions and the activity are a second read it never makes.
 */
export function ProjectCandidatesPage() {
  const { project } = useOutletContext<ProjectOutletContext>();
  const { user } = useAuth();
  const isStaff = canExecuteProjectWork(project, user?.id, user?.workspace?.roles);
  const queryClient = useQueryClient();

  const [query, setQuery] = useState("");
  const debouncedQuery = useDebouncedValue(query.trim(), 250);
  const [status, setStatus] = useState<CandidateStatus | null>(null);
  const [page, setPage] = useState(0);
  const [openCandidateId, setOpenCandidateId] = useState<string | null>(null);
  const [isPickerOpen, setPickerOpen] = useState(false);

  const pipeline = useQuery({
    queryKey: candidatesApi.PIPELINE_KEY(project.id, debouncedQuery, status, page),
    queryFn: ({ signal }) => candidatesApi.getPipeline(project.id, debouncedQuery, status, page, PAGE_SIZE, signal),
    placeholderData: keepPreviousData,
  });
  const rows = useMemo(() => pipeline.data?.candidates ?? [], [pipeline.data]);
  const shownIds = useMemo(() => rows.map((row) => row.id), [rows]);

  const staff = useQuery({
    queryKey: candidatesApi.PIPELINE_STAFF_KEY(project.id, shownIds),
    queryFn: ({ signal }) => candidatesApi.getPipelineStaff(project.id, shownIds, signal),
    enabled: isStaff && shownIds.length > 0,
    placeholderData: keepPreviousData,
  });
  const staffById = useMemo(
    () => new Map((staff.data ?? []).map((row) => [row.candidateId, row])),
    [staff.data],
  );

  const changeStatus = useChangeCandidateStatus(project.id, (saved) => {
    queryClient.setQueryData(candidatesApi.CANDIDATE_KEY(project.id, saved.id), saved);
    void queryClient.invalidateQueries({ queryKey: candidatesApi.CANDIDATES_KEY_PREFIX(project.id) });
  });

  const counts = pipeline.data?.statusCounts ?? {};
  const everyone = Object.values(counts).reduce((total, count) => total + (count ?? 0), 0);
  const presentStatuses = CANDIDATE_STATUSES.filter((option) => (counts[option.value] ?? 0) > 0);
  const totalCount = pipeline.data?.totalCount ?? 0;
  const pageCount = Math.max(1, Math.ceil(totalCount / PAGE_SIZE));

  const handleStatusChip = (next: CandidateStatus | null) => {
    setStatus(next);
    setPage(0);
  };

  return (
    <div className="mx-auto max-w-[1440px] px-4 pb-16 pt-7 md:px-7">
      <div className="mb-4 flex flex-wrap items-start gap-4">
        <div className="min-w-0">
          <h1 className="text-[19px]/[1.25] font-semibold">Candidates</h1>
          <p className="mt-1 font-mono text-[12px] text-u-text3">
            {pipeline.isSuccess
              ? `${everyone} ${everyone === 1 ? "executive" : "executives"} on this position · each one also sits in your workspace candidates, with their notes and history`
              : "Everyone mapped to this position"}
          </p>
        </div>
        {isStaff && (
          <div className="ms-auto flex flex-wrap items-center gap-2">
            <Link
              to="/candidates"
              className="inline-flex items-center gap-1.5 px-2.5 py-1.5 text-[13px] font-medium text-u-text3 hover:text-u-text"
            >
              All candidates
              <Icon d={ICONS.arrowRight} size={13} />
            </Link>
            <Button onClick={() => setPickerOpen(true)}>
              <Icon d={ICONS.userPlus} size={15} />
              Add from your candidates
            </Button>
          </div>
        )}
      </div>

      <div className="mb-3 flex flex-wrap items-center gap-2.5">
        <label className="flex w-[300px] max-w-full items-center gap-2 rounded-[8px] border border-u-border bg-u-raised px-[11px] py-[7px]">
          <Icon d={ICONS.search} size={14} className="flex-none text-u-text3" />
          <input
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setPage(0);
            }}
            placeholder="Search executives…"
            aria-label="Search executives"
            className="w-full bg-transparent font-mono text-[13px] text-u-text outline-none"
          />
        </label>
        <div className="flex flex-wrap gap-1.5" role="group" aria-label="Status">
          <StatusChip label="All" count={everyone} isActive={status === null} onClick={() => handleStatusChip(null)} />
          {presentStatuses.map((option) => (
            <StatusChip
              key={option.value}
              label={option.label}
              count={counts[option.value] ?? 0}
              isActive={status === option.value}
              onClick={() => handleStatusChip(option.value)}
            />
          ))}
        </div>
      </div>

      {pipeline.isError ? (
        <p role="alert" className="rounded-[8px] border border-u-border bg-u-surface px-4 py-10 text-center text-[13px] text-u-text3">
          {messageFor(pipeline.error)}
        </p>
      ) : (
        <div className="overflow-hidden rounded-[8px] border border-u-border bg-u-surface">
          <div className="overflow-x-auto">
            <table aria-label="Candidates on this position" className={cn("w-full", isStaff ? "min-w-[1150px]" : "min-w-[600px]")}>
              <thead>
                <tr className="border-b border-u-border bg-u-raised text-start">
                  <HeaderCell>Executive</HeaderCell>
                  <HeaderCell className="w-[160px]">Status</HeaderCell>
                  {isStaff && <HeaderCell>Tags</HeaderCell>}
                  {isStaff && <HeaderCell>Also in</HeaderCell>}
                  <HeaderCell>Added</HeaderCell>
                  {isStaff && <HeaderCell>Last activity</HeaderCell>}
                </tr>
              </thead>
              <tbody>
                {rows.map((candidate) => (
                  <CandidateRow
                    key={candidate.id}
                    projectId={project.id}
                    candidate={candidate}
                    isStaff={isStaff}
                    staffRow={staffById.get(candidate.id)}
                    isChangingStatus={changeStatus.isPending && changeStatus.variables?.candidateId === candidate.id}
                    onOpen={() => setOpenCandidateId(candidate.id)}
                    onChangeStatus={(next) => changeStatus.mutate({ candidateId: candidate.id, status: next })}
                  />
                ))}
              </tbody>
            </table>
            {pipeline.isSuccess && rows.length === 0 && (
              <p className="px-4 py-10 text-center font-mono text-[13px] text-u-text3">
                {everyone === 0 && !debouncedQuery
                  ? "Nobody is on this position yet."
                  : "Nobody on this position matches. Clear the search or pick another status."}
              </p>
            )}
          </div>
        </div>
      )}

      {pageCount > 1 && (
        <div className="mt-3 flex items-center justify-end gap-2 font-mono text-[12px] text-u-text3">
          <Button variant="secondary" className="px-3 py-1.5 text-[12.5px]" disabled={page === 0} onClick={() => setPage(page - 1)}>
            Previous
          </Button>
          <span>
            Page {page + 1} of {pageCount}
          </span>
          <Button variant="secondary" className="px-3 py-1.5 text-[12.5px]" disabled={page + 1 >= pageCount} onClick={() => setPage(page + 1)}>
            Next
          </Button>
        </div>
      )}

      {isStaff && (
        <p className="mx-0.5 mt-3 font-mono text-[11.5px] text-u-text3">
          Anyone added here — from the Companies grid, the plugin, an import or Find executives — joins your workspace
          candidates too. Someone already there is added to this position, never duplicated.
        </p>
      )}

      {isPickerOpen && <AddFromPoolPicker projectId={project.id} onClose={() => setPickerOpen(false)} />}
      <ReportCandidateDrawer project={project} candidateId={openCandidateId} onClose={() => setOpenCandidateId(null)} />
    </div>
  );
}

function CandidateRow({
  projectId,
  candidate,
  isStaff,
  staffRow,
  isChangingStatus,
  onOpen,
  onChangeStatus,
}: {
  projectId: string;
  candidate: Candidate;
  isStaff: boolean;
  staffRow: CandidatePipelineStaffRow | undefined;
  isChangingStatus: boolean;
  onOpen: () => void;
  onChangeStatus: (status: CandidateStatus) => void;
}) {
  const { tagsById } = usePoolLookups();
  const headline = [candidate.title, candidate.companyName].filter(Boolean).join(" · ");
  const statusStyle = candidateStatusStyle(candidate.status);
  const source = CANDIDATE_SOURCE_STYLES[candidate.source]?.label;
  const latest = staffRow?.lastActivity ? lastActivityOf(staffRow.lastActivity) : null;

  return (
    <tr onClick={onOpen} className="cursor-pointer border-b border-u-border last:border-b-0 hover:bg-u-raised">
      <td className="px-4 py-2.5">
        <span className="flex min-w-0 items-center gap-2.5">
          <CandidateAvatar projectId={projectId} candidate={candidate} size="sm" />
          <span className="min-w-0">
            <span className="flex items-center gap-1.5">
              <span className="truncate text-[13px] font-semibold text-u-text">{candidate.fullName}</span>
              {staffRow?.doNotContact && (
                <span title="Do not contact" className="inline-flex flex-none text-u-offlimits">
                  <Icon d={ICONS.ban} size={12} />
                  <span className="sr-only">Do not contact</span>
                </span>
              )}
            </span>
            {headline && <span className="block truncate font-mono text-[11.5px] text-u-text3">{headline}</span>}
          </span>
        </span>
      </td>
      <td className="px-3 py-2.5">
        {isStaff ? (
          <select
            value={candidate.status}
            disabled={isChangingStatus}
            onClick={(event) => event.stopPropagation()}
            onChange={(event) => onChangeStatus(event.target.value as CandidateStatus)}
            aria-label={`Status of ${candidate.fullName}`}
            className="max-w-full rounded-[6px] border border-u-border bg-u-raised px-2 py-1 font-mono text-[12px] text-u-text outline-none"
          >
            {CANDIDATE_STATUSES.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        ) : (
          <span className={cn("inline-flex rounded-[5px] px-2 py-0.5 font-mono text-[11.5px]", statusStyle.className)}>
            {statusStyle.label}
          </span>
        )}
      </td>
      {isStaff && (
        <td className="px-3 py-2.5">
          <span className="flex flex-wrap gap-1">
            {(staffRow?.tagIds ?? []).map((tagId) => {
              const tag = tagsById.get(tagId);
              return tag ? <TagPill key={tagId} tag={tag} /> : null;
            })}
          </span>
        </td>
      )}
      {isStaff && (
        <td className="px-3 py-2.5">
          {staffRow && staffRow.alsoIn.length > 0 ? (
            <span className="flex flex-wrap gap-1">
              {staffRow.alsoIn.map((position) => {
                const label = `${position.positionTitle ?? "A position"} · ${candidateStatusStyle(position.status).label}`;
                return (
                  <span
                    key={position.candidateId}
                    title={label}
                    className="inline-flex items-center whitespace-nowrap rounded-[5px] border border-u-border bg-u-raised px-[7px] py-0.5 text-[11.5px] font-medium text-u-text2"
                  >
                    {label}
                  </span>
                );
              })}
            </span>
          ) : (
            <span className="font-mono text-[12px] text-u-text3">—</span>
          )}
        </td>
      )}
      <td className="truncate px-3 py-2.5 font-mono text-[12px] text-u-text3">
        {isStaff
          ? [staffRow?.addedByName, shortWhen(candidate.addedAt), source].filter(Boolean).join(" · ")
          : formatInstantDate(candidate.addedAt)}
      </td>
      {isStaff && (
        <td className="px-3 py-2.5">
          <span className="block truncate text-[12.5px] text-u-text2">{latest?.text ?? "Added to this position"}</span>
          <span className="block truncate font-mono text-[11px] text-u-text3">{latest?.meta ?? ""}</span>
        </td>
      )}
    </tr>
  );
}

function StatusChip({
  label,
  count,
  isActive,
  onClick,
}: {
  label: string;
  count: number;
  isActive: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      aria-pressed={isActive}
      onClick={onClick}
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-[11px] py-[5px] font-mono text-[12px] hover:text-u-text",
        isActive ? "border-transparent bg-u-accent-tint text-u-accent" : "border-u-border text-u-text2",
      )}
    >
      {label}
      <span className="text-u-text3">{count}</span>
    </button>
  );
}

function HeaderCell({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <th
      scope="col"
      className={cn(
        "whitespace-nowrap px-3 py-2.5 text-start text-[11px] font-semibold uppercase tracking-[0.04em] text-u-text3 first:ps-4",
        className,
      )}
    >
      {children}
    </th>
  );
}
