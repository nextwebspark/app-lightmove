import { useInfiniteQuery, useMutation, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Avatar, Button, DateInput, Spinner, TableSkeleton, useToast } from "../../../components/ui";
import { PillSelect } from "../../../components/ui/PillSelect";
import { messageFor } from "../../../lib/errorCodes";
import { formatActivityTime } from "../../projects/lib/activity";
import * as auditLogApi from "../api/auditLogApi";
import type { AuditLogEntry, AuditLogGroup } from "../api/types";

const COLUMNS = ["Time", "Person", "Event", "Position", "IP address"];

const CATEGORIES: readonly { value: AuditLogGroup; label: string }[] = [
  { value: "signIn", label: "Sign-in" },
  { value: "membersAndAccess", label: "Members & access" },
  { value: "exportsAndDownloads", label: "Exports & downloads" },
  { value: "settings", label: "Settings" },
  { value: "apiKeysAndAiApps", label: "API keys & AI apps" },
  { value: "outreach", label: "Outreach" },
];

const TH = "type-micro-label whitespace-nowrap border-b border-u-border-strong px-3 py-[9px] text-left font-mono text-u-text3";
const TD = "border-b border-u-border px-3 py-2.5 align-top text-body";

/**
 * Settings → Security → Audit log: who did what in the workspace, newest first, narrowed by a filter row and taken
 * as a CSV of the same view. Every line is the server's own sentence; nothing here reads an event's payload.
 */
export function AuditLogPanel() {
  const toast = useToast();
  const [filters, setFilters] = useState<auditLogApi.AuditLogFilters>(auditLogApi.NO_AUDIT_LOG_FILTERS);
  const change = (patch: Partial<auditLogApi.AuditLogFilters>) => setFilters((current) => ({ ...current, ...patch }));

  const people = useQuery({
    queryKey: auditLogApi.AUDIT_LOG_PEOPLE_KEY,
    queryFn: ({ signal }) => auditLogApi.auditLogPeople(signal),
  });

  const log = useInfiniteQuery({
    queryKey: auditLogApi.AUDIT_LOG_PAGE_KEY(filters),
    queryFn: ({ pageParam, signal }) => auditLogApi.auditLog(filters, pageParam, signal),
    initialPageParam: null as number | null,
    getNextPageParam: (last) => last.nextCursor,
  });
  const entries = log.data?.pages.flatMap((page) => page.entries) ?? [];

  const exportCsv = useMutation({
    mutationFn: () => auditLogApi.saveAuditLogCsv(filters),
    onSuccess: () => {
      toast("Exported as a CSV — recorded in the audit log");
      void log.refetch();
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  return (
    <div className="rounded-u-card border border-u-border bg-u-raised p-5">
      <div className="mb-1 text-body font-semibold">Audit log</div>
      <p className="mb-4 text-note text-u-text3">
        Sign-ins, changes to members and access, exports and downloads, settings, API keys and AI apps, and
        outreach in this workspace.
      </p>

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <DateInput
          value={filters.fromDay}
          max={filters.toDay || undefined}
          onChange={(fromDay) => change({ fromDay })}
          ariaLabel="From"
          className="w-[150px]"
        />
        <span className="text-note text-u-text3">to</span>
        <DateInput
          value={filters.toDay}
          min={filters.fromDay || undefined}
          onChange={(toDay) => change({ toDay })}
          ariaLabel="To"
          className="w-[150px]"
        />
        <PillSelect label="Person" value={filters.actor} onChange={(actor) => change({ actor })} className="max-w-[240px]">
          <option value="">Anyone</option>
          {(people.data ?? []).map((person) => (
            <option key={person.userId} value={person.userId}>
              {(person.name ?? "Unnamed") + (person.removed ? " (removed)" : "")}
            </option>
          ))}
        </PillSelect>
        <PillSelect
          label="Category"
          value={filters.group}
          onChange={(group) => change({ group: group as AuditLogGroup | "" })}
        >
          <option value="">All</option>
          {CATEGORIES.map((category) => (
            <option key={category.value} value={category.value}>
              {category.label}
            </option>
          ))}
        </PillSelect>
        <PillSelect
          label="Outcome"
          value={filters.outcome}
          onChange={(outcome) => change({ outcome: outcome as auditLogApi.AuditLogFilters["outcome"] })}
        >
          <option value="">Any</option>
          <option value="SUCCESS">Succeeded</option>
          <option value="FAILURE">Failed</option>
        </PillSelect>
        <Button
          variant="secondary"
          size="sm"
          className="ms-auto"
          loading={exportCsv.isPending}
          onClick={() => exportCsv.mutate()}
        >
          Export CSV
        </Button>
      </div>

      {log.isError ? (
        <p role="alert" className="text-body text-u-text3">
          {messageFor(log.error)}
        </p>
      ) : log.isPending ? (
        <TableSkeleton columns={COLUMNS} rows={5} />
      ) : entries.length === 0 ? (
        <p className="py-6 text-center text-body text-u-text3">
          No activity matches these filters. Widen the dates or pick another category.
        </p>
      ) : (
        <>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[680px] border-collapse">
              <thead>
                <tr>
                  {COLUMNS.map((column) => (
                    <th key={column} className={TH}>
                      {column}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {entries.map((entry) => (
                  <AuditLogRow key={entry.id} entry={entry} />
                ))}
              </tbody>
            </table>
          </div>
          {log.hasNextPage && (
            <button
              type="button"
              onClick={() => void log.fetchNextPage()}
              disabled={log.isFetchingNextPage}
              className="mt-3 inline-flex items-center gap-1.5 text-note font-medium text-u-accent hover:underline disabled:opacity-60"
            >
              {log.isFetchingNextPage && <Spinner />}
              Load more
            </button>
          )}
        </>
      )}
    </div>
  );
}

function AuditLogRow({ entry }: { entry: AuditLogEntry }) {
  const name = entry.actorName ?? "Someone";
  return (
    <tr>
      <td className={`${TD} whitespace-nowrap font-mono text-meta text-u-text3`}>
        <time dateTime={entry.occurredAt} title={new Date(entry.occurredAt).toLocaleString()}>
          {formatActivityTime(entry.occurredAt)}
        </time>
      </td>
      <td className={`${TD} whitespace-nowrap`}>
        <span className="inline-flex items-center gap-2">
          {entry.actorKind === "MEMBER" && (
            <Avatar id={entry.actorUserId ?? `nobody-${entry.id}`} name={name} src={entry.actorAvatarUrl} size="sm" />
          )}
          <span className="font-medium text-u-text">{name}</span>
          {entry.actorRemoved && <span className="text-note text-u-text3">(removed)</span>}
        </span>
      </td>
      <td className={TD}>
        <span className="text-u-text2">{entry.summary}</span>
        {entry.outcome === "FAILURE" && (
          <span className="type-tag ms-2 inline-block rounded-full bg-u-offlimits-tint px-2 py-0.5 text-u-offlimits">
            Failed
          </span>
        )}
      </td>
      <td className={`${TD} text-u-text2`}>{entry.positionTitle ?? ""}</td>
      <td className={`${TD} whitespace-nowrap font-mono text-meta text-u-text3`}>{entry.ipAddress ?? ""}</td>
    </tr>
  );
}
