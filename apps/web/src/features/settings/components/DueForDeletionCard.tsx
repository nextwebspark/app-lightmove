import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, LinesSkeleton, useToast } from "../../../components/ui";
import { ConfirmDialog } from "../../../components/ui/ConfirmDialog";
import { SelectionCheckbox } from "../../../components/ui/SelectionCheckbox";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { POOL_KEY } from "../../candidates/api/poolApi";
import * as dataRetentionApi from "../api/dataRetentionApi";
import type { RetentionDuePerson } from "../api/dataRetentionApi";
import {
  dayOf,
  deleteTitle,
  dueEmptyBody,
  dueSubtitle,
  erasedToast,
  lastActivityPhrase,
  namesSentence,
  peopleCount,
} from "../lib/dataRetention";

const ROW_GRID = "grid grid-cols-[24px_minmax(0,1fr)] items-center gap-3 md:grid-cols-[24px_minmax(0,1fr)_130px_120px_130px]";

/**
 * The people past the saved period, for an admin to delete or keep (`Settings.dc.html`, `data`). Nothing leaves
 * without this review; a delete is the same erasure as Candidates' own, never suppressing anyone, since passing the
 * period is no objection.
 */
export function DueForDeletionCard({ savedMonths }: { savedMonths: number | null }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set());
  const [confirming, setConfirming] = useState(false);

  const due = useQuery({
    queryKey: dataRetentionApi.RETENTION_DUE_KEY,
    queryFn: ({ signal }) => dataRetentionApi.getRetentionDue(signal),
  });
  const people = due.data?.people ?? [];
  const chosen = people.filter((person) => selected.has(person.personId));

  const settle = () => {
    setSelected(new Set());
    void queryClient.invalidateQueries({ queryKey: dataRetentionApi.RETENTION_DUE_KEY });
  };
  const keep = useMutation({
    mutationFn: () => dataRetentionApi.keepDue(chosen.map((person) => person.personId)),
    onSuccess: ({ kept }) => {
      settle();
      toast(`${peopleCount(kept)} kept for another ${savedMonths} months`);
    },
    onError: (error) => toast.error(messageFor(error)),
  });
  const erase = useMutation({
    mutationFn: () => dataRetentionApi.eraseDue(chosen.map((person) => person.personId)),
    onSuccess: (result) => {
      setConfirming(false);
      settle();
      void queryClient.invalidateQueries({ queryKey: POOL_KEY });
      toast(erasedToast(result));
    },
    onError: (error) => {
      setConfirming(false);
      settle();
      void queryClient.invalidateQueries({ queryKey: POOL_KEY });
      toast.error(messageFor(error));
    },
  });

  const toggle = (personId: string) =>
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(personId)) next.delete(personId);
      else next.add(personId);
      return next;
    });
  const allOn = people.length > 0 && chosen.length === people.length;
  const toggleAll = () => setSelected(allOn ? new Set() : new Set(people.map((person) => person.personId)));
  const hasRows = savedMonths !== null && people.length > 0;

  return (
    <section aria-label="Due for deletion" className="mt-4 rounded-u-card border border-u-border bg-u-raised p-5">
      <div className="flex flex-wrap items-start gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <span className="text-body font-semibold text-u-text">Due for deletion</span>
            {hasRows && due.data && (
              <span className="rounded-u-control bg-u-signal-tint px-1.5 py-px font-mono text-eyebrow text-u-signal">
                {peopleCount(due.data.total)}
              </span>
            )}
          </div>
          <p className="mt-0.5 max-w-[560px] font-mono text-meta text-u-text3">{dueSubtitle(savedMonths)}</p>
        </div>
        {hasRows && (
          <div className="flex flex-none items-center gap-2">
            <span className="font-mono text-meta text-u-text3">
              {chosen.length === 0 ? "Select people to act on" : `${chosen.length} selected`}
            </span>
            <Button
              variant="secondary"
              size="sm"
              disabled={chosen.length === 0}
              loading={keep.isPending}
              title={`Keep for another ${savedMonths} months from today`}
              onClick={() => keep.mutate()}
            >
              Keep
            </Button>
            <Button variant="danger" size="sm" disabled={chosen.length === 0} onClick={() => setConfirming(true)}>
              Delete selected
            </Button>
          </div>
        )}
      </div>

      {due.isError ? (
        <p className="mt-3.5 text-note text-u-text3">{messageFor(due.error)}</p>
      ) : due.isPending ? (
        <div className="mt-3.5">
          <LinesSkeleton />
        </div>
      ) : hasRows ? (
        <div role="table" aria-label="Due for deletion" className="mt-3.5">
          <div
            role="row"
            className={cn(
              ROW_GRID,
              "border-b border-u-border pb-2 font-mono text-eyebrow font-semibold uppercase tracking-[.1em] text-u-text3",
            )}
          >
            <span role="columnheader">
              <SelectionCheckbox
                label="Select everyone due"
                checked={allOn}
                indeterminate={!allOn && chosen.length > 0}
                onChange={toggleAll}
              />
            </span>
            <span role="columnheader">Candidate</span>
            <span role="columnheader" className="hidden md:block">Last activity</span>
            <span role="columnheader" className="hidden md:block">Positions</span>
            <span role="columnheader" className="hidden md:block">Owner</span>
          </div>
          {people.map((person, index) => (
            <DueRow
              key={person.personId}
              person={person}
              first={index === 0}
              checked={selected.has(person.personId)}
              onToggle={() => toggle(person.personId)}
            />
          ))}
          {due.data.total > people.length && (
            <p className="mt-3 font-mono text-meta text-u-text3">
              Showing the {people.length} untouched longest of {due.data.total}. Keep or delete them to see the rest.
            </p>
          )}
        </div>
      ) : (
        <div className="mt-3.5 rounded-u-card border border-dashed border-u-border-strong bg-u-surface px-5 py-6 text-center">
          <span className="mx-auto mb-2.5 grid size-9 place-items-center rounded-u-card bg-u-direct-tint text-u-direct">
            <Icon d={ICONS.check} size={17} />
          </span>
          <div className="text-note font-semibold text-u-text">Nobody is due for deletion</div>
          <p className="mx-auto mt-1 max-w-[420px] font-mono text-meta text-u-text3">{dueEmptyBody(savedMonths)}</p>
        </div>
      )}

      <ConfirmDialog
        open={confirming && chosen.length > 0}
        title={deleteTitle(chosen.length)}
        confirmLabel="Delete permanently"
        pending={erase.isPending}
        onConfirm={() => erase.mutate()}
        onClose={() => setConfirming(false)}
      >
        <p>
          <b className="font-semibold text-u-text">{namesSentence(chosen.map((person) => person.fullName))}</b> and
          everything Uncava holds about them go now: profile, contacts, notes, documents, emails, meetings and
          recordings, on every position. This cannot be undone.
        </p>
        <p className="font-mono text-meta text-u-text3">
          The audit log records that they were deleted under your retention period, never their names. Copies in
          Uncava's backups age out within 30 days.
        </p>
      </ConfirmDialog>
    </section>
  );
}

function DueRow({
  person,
  first,
  checked,
  onToggle,
}: {
  person: RetentionDuePerson;
  first: boolean;
  checked: boolean;
  onToggle: () => void;
}) {
  const meta = [person.title, person.employer].filter(Boolean).join(" · ");
  const last = `${dayOf(person.lastActivityAt)} · ${lastActivityPhrase(person.lastActivityKind)}`;
  return (
    <div
      role="row"
      className={cn(ROW_GRID, "py-2.5", !first && "border-t border-u-border", checked && "bg-u-accent-tint")}
    >
      <span role="cell">
        <SelectionCheckbox label={`Select ${person.fullName}`} checked={checked} onChange={onToggle} />
      </span>
      <div role="cell" className="min-w-0">
        <div className="truncate text-note font-medium text-u-text">{person.fullName}</div>
        {meta && <div className="truncate font-mono text-meta text-u-text3">{meta}</div>}
        <div className="truncate font-mono text-meta text-u-text3 md:hidden">{last}</div>
      </div>
      <div role="cell" className="hidden min-w-0 md:block">
        <div className="font-mono text-meta text-u-text2">{dayOf(person.lastActivityAt)}</div>
        <div className="truncate font-mono text-meta text-u-text3">{lastActivityPhrase(person.lastActivityKind)}</div>
      </div>
      <span role="cell" className="hidden font-mono text-meta text-u-text2 md:block">
        {person.positions} · none live
      </span>
      <span role="cell" className="hidden truncate font-mono text-meta text-u-text2 md:block">
        {person.ownerName ?? "Nobody"}
      </span>
    </div>
  );
}
