import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { ActionMenu, MenuItem, MenuSeparator } from "../../../components/ui/ActionMenu";
import { Button, Input, useToast } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { liveStages } from "../../candidates/lib/pipelineStatus";
import * as pipelineApi from "../../workspace/api/pipelineApi";
import type {
  ExitReason,
  ExitReasonCatalog,
  ExitReasonChange,
  PipelineCatalog,
  PipelineStatus,
} from "../../workspace/api/types";

export const EXIT_REASON_NAME_LIMIT = 60;

/**
 * Settings → Pipeline stages' "Reasons for leaving the process" (`SettingsPipelineStages.dc.html`): which reasons
 * each out-of-process status offers. Staff read it; only an admin changes it.
 */
export function ExitReasonsCard({ catalog, isAdmin }: { catalog: PipelineCatalog; isAdmin: boolean }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [openId, setOpenId] = useState<string | null>(null);
  const [renamingId, setRenamingId] = useState<string | null>(null);
  const [draft, setDraft] = useState("");
  const [newReason, setNewReason] = useState("");

  const query = useQuery({
    queryKey: pipelineApi.EXIT_REASONS_KEY,
    queryFn: ({ signal }) => pipelineApi.exitReasons(signal),
  });

  const settle = (next: ExitReasonCatalog, message?: string) => {
    queryClient.setQueryData(pipelineApi.EXIT_REASONS_KEY, next);
    if (message) toast(message);
  };
  const fail = (error: unknown) => {
    toast.error(messageFor(error));
    void queryClient.invalidateQueries({ queryKey: pipelineApi.EXIT_REASONS_KEY });
  };

  const update = useMutation({
    mutationFn: ({ id, change }: { id: string; change: ExitReasonChange; message?: string }) =>
      pipelineApi.updateExitReason(id, change),
    onSuccess: (next, { message }) => settle(next, message),
    onError: fail,
  });
  const create = useMutation({
    mutationFn: (label: string) => pipelineApi.createExitReason(label),
    onSuccess: (next, label) => {
      settle(next, "Reason added. Tick where it is offered");
      setNewReason("");
      setOpenId(next.reasons.find((reason) => reason.label === label)?.id ?? null);
    },
    onError: fail,
  });

  const reasons = query.data?.reasons ?? [];
  const live = reasons.filter((reason) => !reason.retired);
  const retired = reasons.filter((reason) => reason.retired);
  const editable = isAdmin && !update.isPending;

  const handleAdd = (event: FormEvent) => {
    event.preventDefault();
    const label = newReason.trim();
    if (label) create.mutate(label);
  };
  const handleRename = (event: FormEvent, reason: ExitReason) => {
    event.preventDefault();
    const label = draft.trim();
    if (!label) return;
    update.mutate(
      { id: reason.id, change: { label }, message: "Renamed everywhere it is offered" },
      { onSuccess: () => setRenamingId(null) },
    );
  };
  const handleTick = (reason: ExitReason, statusId: string) =>
    update.mutate({
      id: reason.id,
      change: {
        statusIds: reason.statusIds.includes(statusId)
          ? reason.statusIds.filter((id) => id !== statusId)
          : [...reason.statusIds, statusId],
      },
    });

  return (
    <section
      id="pipeline-reasons"
      aria-label="Reasons for leaving the process"
      className="mt-4 rounded-u-card border border-u-border bg-u-raised p-5"
    >
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-body font-semibold text-u-text">Reasons for leaving the process</span>
        {!isAdmin && (
          <span className="rounded bg-u-surface px-1.5 py-px font-mono text-meta font-medium text-u-text3">Admin</span>
        )}
      </div>
      <p className="mt-0.5 max-w-[620px] font-mono text-meta text-u-text3">
        When someone is moved to a status out of process, Uncava asks why, from the reasons ticked for that status. A
        status with no reasons asks for none. Reasons are seen by your team only, never by a client.
      </p>

      {query.isError ? (
        <p className="mt-3 text-note text-u-text3">{messageFor(query.error)}</p>
      ) : (
        <ul className="mt-2 divide-y divide-u-border border-t border-u-border">
          {live.map((reason) => (
            <li key={reason.id}>
              <div className="flex flex-wrap items-center gap-2.5 py-2.5">
                {renamingId === reason.id ? (
                  <form className="flex items-center gap-1" onSubmit={(event) => handleRename(event, reason)}>
                    <Input
                      value={draft}
                      onChange={(event) => setDraft(event.target.value)}
                      aria-label="Reason name"
                      maxLength={EXIT_REASON_NAME_LIMIT}
                      autoFocus
                      className="w-40"
                    />
                    <Button type="submit" variant="ghost" size="xs" disabled={!draft.trim()}>
                      Save
                    </Button>
                  </form>
                ) : (
                  <span className="w-40 flex-none text-body text-u-text">{reason.label}</span>
                )}
                {reason.requiresNote && (
                  <span className="flex-none rounded bg-u-accent-tint px-1.5 py-px font-mono text-meta font-medium text-u-accent">
                    Asks for a note
                  </span>
                )}
                <span
                  title={offeredAt(catalog, reason)}
                  className={cn(
                    "min-w-0 flex-1 truncate font-mono text-meta",
                    reason.statusIds.length ? "text-u-text3" : "text-u-signal",
                  )}
                >
                  {offeredAt(catalog, reason)}
                </span>
                {isAdmin && renamingId !== reason.id && (
                  <span className="ms-auto flex items-center gap-1">
                    <Button
                      variant="secondary"
                      size="xs"
                      aria-expanded={openId === reason.id}
                      onClick={() => setOpenId(openId === reason.id ? null : reason.id)}
                    >
                      {openId === reason.id ? "Done" : "Statuses"}
                    </Button>
                    <ActionMenu label={`${reason.label} options`} width={220}>
                      {(close) => (
                        <>
                          <MenuItem
                            icon={ICONS.pencil}
                            onSelect={() => {
                              close();
                              setDraft(reason.label);
                              setRenamingId(reason.id);
                            }}
                          >
                            Rename
                          </MenuItem>
                          <MenuItem
                            icon={ICONS.noteBubble}
                            disabled={!editable}
                            onSelect={() => {
                              close();
                              update.mutate({
                                id: reason.id,
                                change: { requiresNote: !reason.requiresNote },
                                message: reason.requiresNote ? "No note asked for" : "A note is asked for beside it",
                              });
                            }}
                          >
                            {reason.requiresNote ? "Stop asking for a note" : "Ask for a note"}
                          </MenuItem>
                          <MenuSeparator />
                          <MenuItem
                            icon={ICONS.archive}
                            tone="danger"
                            disabled={!editable}
                            onSelect={() => {
                              close();
                              setOpenId(null);
                              update.mutate({
                                id: reason.id,
                                change: { retired: true },
                                message: "Retired. Candidates given it keep it",
                              });
                            }}
                          >
                            Retire reason
                          </MenuItem>
                        </>
                      )}
                    </ActionMenu>
                  </span>
                )}
              </div>
              {openId === reason.id && (
                <div
                  role="group"
                  aria-label={`Statuses that offer ${reason.label}`}
                  className="mb-3 grid grid-cols-[repeat(auto-fill,minmax(180px,1fr))] gap-x-4 gap-y-2.5 rounded-u-control border border-u-border bg-u-surface px-3.5 py-2.5"
                >
                  {waysOutByStage(catalog, reason).map((group) => (
                    <div key={group.id}>
                      <div className="type-summary-label mb-1 text-u-text3">{group.label}</div>
                      {group.statuses.map((status) => (
                        <label key={status.id} className="flex cursor-pointer items-center gap-2 py-0.5 text-note text-u-text">
                          <input
                            type="checkbox"
                            className="size-3.5 accent-u-accent"
                            checked={reason.statusIds.includes(status.id)}
                            disabled={!editable}
                            onChange={() => handleTick(reason, status.id)}
                          />
                          {status.label}
                        </label>
                      ))}
                    </div>
                  ))}
                </div>
              )}
            </li>
          ))}
        </ul>
      )}

      {retired.length > 0 && (
        <>
          <div className="type-summary-label mt-2 text-u-text3">Retired</div>
          <ul className="divide-y divide-u-border border-t border-u-border">
            {retired.map((reason) => (
              <li key={reason.id} className="flex flex-wrap items-center gap-2.5 py-2.5 opacity-60">
                <span className="w-40 flex-none text-body text-u-text">{reason.label}</span>
                <span className="min-w-0 flex-1 truncate font-mono text-meta text-u-text3">
                  Candidates given it keep it; it is offered no more
                </span>
                {isAdmin && (
                  <Button
                    variant="ghost"
                    size="xs"
                    disabled={!editable}
                    onClick={() => update.mutate({ id: reason.id, change: { retired: false }, message: "Restored" })}
                  >
                    Restore
                  </Button>
                )}
              </li>
            ))}
          </ul>
        </>
      )}

      {isAdmin && query.isSuccess && (
        <form className="mt-3 flex flex-wrap items-center gap-2" onSubmit={handleAdd}>
          <Input
            value={newReason}
            onChange={(event) => setNewReason(event.target.value)}
            placeholder="New reason, e.g. Notice period"
            aria-label="New reason"
            maxLength={EXIT_REASON_NAME_LIMIT}
            className="max-w-[240px]"
          />
          <Button type="submit" variant="secondary" size="sm" disabled={!newReason.trim()} loading={create.isPending}>
            <Icon d={ICONS.plus} size={14} />
            Add reason
          </Button>
        </form>
      )}
    </section>
  );
}

/** Where a reason is offered, a name two stages share told apart by its stage. */
function offeredAt(catalog: PipelineCatalog, reason: ExitReason): string {
  if (reason.statusIds.length === 0) return "Not offered at any status yet";
  const statuses = catalog.stages.flatMap((stage) => stage.statuses.map((status) => ({ stage, status })));
  const shared = (label: string) =>
    statuses.filter(({ status }) => status.outOfProcess && !status.retired && status.label === label).length > 1;
  return statuses
    .filter(({ status }) => reason.statusIds.includes(status.id))
    .map(({ stage, status }) => (shared(status.label) ? `${status.label} (${stage.label})` : status.label))
    .join(" · ");
}

/** Every live stage's ways out, a retired one kept only where this reason already offers it. */
function waysOutByStage(
  catalog: PipelineCatalog,
  reason: ExitReason,
): { id: string; label: string; statuses: PipelineStatus[] }[] {
  return liveStages(catalog)
    .map((stage) => ({
      id: stage.id,
      label: stage.label,
      statuses: stage.statuses.filter(
        (status) => status.outOfProcess && (!status.retired || reason.statusIds.includes(status.id)),
      ),
    }))
    .filter((group) => group.statuses.length > 0);
}
