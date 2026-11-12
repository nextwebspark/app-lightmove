import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Field, Input, LinesSkeleton, Modal, TextArea, useToast } from "../../../components/ui";
import { ChoiceCardGroup } from "../../../components/ui/ChoiceCardGroup";
import { ConfirmDialog } from "../../../components/ui/ConfirmDialog";
import { ApiRequestError } from "../../../lib/apiClient";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import * as dataRetentionApi from "../api/dataRetentionApi";
import type { Suppression } from "../api/dataRetentionApi";
import {
  dayOf,
  linkedinProfileProblem,
  suppressionKindLabel,
  suppressionReasonLabel,
  suppressionRemoveBody,
} from "../lib/dataRetention";

const NOTE_LIMIT = 500;
const ROW_GRID = "grid grid-cols-[86px_minmax(0,1fr)_62px] items-center gap-3 md:grid-cols-[86px_120px_minmax(0,1fr)_120px_92px_62px]";

const REASON_OPTIONS = [
  { value: "manual", title: "Added by an admin", body: "Your firm's own decision, such as a client's board member." },
  { value: "objected", title: "Objected", body: "They asked not to be processed or contacted." },
] as const;

/**
 * The people no door may file again (`Settings.dc.html`, `data`). Each entry is a one-way hash of a profile or an
 * address, so a row says only its kind, reason, note and who added it when; lifting one lets that person back in.
 */
export function SuppressionListCard() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [adding, setAdding] = useState(false);
  const [removing, setRemoving] = useState<Suppression | null>(null);

  const list = useQuery({
    queryKey: dataRetentionApi.SUPPRESSIONS_KEY,
    queryFn: ({ signal }) => dataRetentionApi.getSuppressions(signal),
  });
  const remove = useMutation({
    mutationFn: (suppressionId: string) => dataRetentionApi.removeSuppression(suppressionId),
    onSuccess: () => {
      setRemoving(null);
      void queryClient.invalidateQueries({ queryKey: dataRetentionApi.SUPPRESSIONS_KEY });
      toast("Removed from the suppression list");
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  return (
    <section aria-label="Suppression list" className="mt-4 rounded-u-card border border-u-border bg-u-raised p-5">
      <div className="flex flex-wrap items-start gap-3">
        <div className="min-w-0 flex-1">
          <div className="text-body font-semibold text-u-text">Suppression list</div>
          <p className="mt-0.5 max-w-[560px] font-mono text-meta text-u-text3">
            People who must never be added again. The browser plugin, imports, Find executives, People search and
            adding by hand all check it first, and stop.
          </p>
        </div>
        <Button variant="secondary" size="sm" onClick={() => setAdding(true)}>
          <Icon d={ICONS.plus} size={14} />
          Add to suppression list
        </Button>
      </div>
      <div className="mt-3 flex gap-2.5 rounded-u-chip border border-u-border bg-u-surface px-3 py-2.5 font-mono text-meta text-u-text2">
        <Icon d={ICONS.lock} size={15} className="mt-px flex-none text-u-text3" />
        <span>
          Each entry is kept as a one-way hash of a LinkedIn profile or an email address, so the list can't be read
          back into names. Only the note you leave says who an entry is for.
        </span>
      </div>

      {list.isError ? (
        <p className="mt-3.5 text-note text-u-text3">{messageFor(list.error)}</p>
      ) : list.isPending ? (
        <div className="mt-3.5">
          <LinesSkeleton />
        </div>
      ) : list.data.length === 0 ? (
        <p className="mt-3.5 pb-1 pt-4 text-center font-mono text-meta text-u-text3">
          Nobody is on the list. A permanent deletion can add someone, or you can add them here.
        </p>
      ) : (
        <div role="table" aria-label="Suppression list" className="mt-3.5">
          <div
            role="row"
            className={cn(
              ROW_GRID,
              "border-b border-u-border pb-2 font-mono text-eyebrow font-semibold uppercase tracking-[.1em] text-u-text3",
            )}
          >
            <span role="columnheader">Kind</span>
            <span role="columnheader" className="hidden md:block">Reason</span>
            <span role="columnheader">Note</span>
            <span role="columnheader" className="hidden md:block">Added by</span>
            <span role="columnheader" className="hidden md:block">Date</span>
            <span role="columnheader" />
          </div>
          {list.data.map((entry, index) => (
            <div key={entry.id} role="row" className={cn(ROW_GRID, "py-2", index > 0 && "border-t border-u-border")}>
              <span role="cell">
                <span
                  className={cn(
                    "inline-flex rounded-full px-2 py-0.5 font-mono text-eyebrow font-semibold uppercase tracking-[.05em]",
                    entry.kind === "email" ? "bg-u-adjacent-tint text-u-adjacent" : "border border-u-border text-u-text2",
                  )}
                >
                  {suppressionKindLabel(entry.kind)}
                </span>
              </span>
              <span
                role="cell"
                className={cn(
                  "hidden text-meta font-medium md:block",
                  entry.reason === "erased" ? "text-u-offlimits" : entry.reason === "objected" ? "text-u-signal" : "text-u-text2",
                )}
              >
                {suppressionReasonLabel(entry.reason)}
              </span>
              <span
                role="cell"
                title={entry.note ?? undefined}
                className={cn("line-clamp-2 min-w-0 text-meta", entry.note ? "text-u-text" : "text-u-text3")}
              >
                {entry.note || "—"}
              </span>
              <span role="cell" className="hidden truncate font-mono text-meta text-u-text2 md:block">
                {entry.addedBy?.name ?? "—"}
              </span>
              <span role="cell" className="hidden font-mono text-meta text-u-text3 md:block">
                {dayOf(entry.createdAt)}
              </span>
              <span role="cell" className="justify-self-end">
                <Button variant="ghost" size="xs" className="text-u-offlimits" onClick={() => setRemoving(entry)}>
                  Remove
                </Button>
              </span>
            </div>
          ))}
        </div>
      )}
      <p className="mt-3 font-mono text-meta text-u-text3">
        Every entry added or removed is recorded in the audit log.
      </p>

      {adding && <AddSuppressionModal onClose={() => setAdding(false)} />}
      <ConfirmDialog
        open={removing !== null}
        title="Remove from the suppression list?"
        confirmLabel="Remove"
        pending={remove.isPending}
        onConfirm={() => removing && remove.mutate(removing.id)}
        onClose={() => setRemoving(null)}
      >
        <p>{removing ? suppressionRemoveBody(removing.reason) : ""}</p>
        <p className="font-mono text-meta text-u-text3">The audit log records that you removed it.</p>
      </ConfirmDialog>
    </section>
  );
}

function AddSuppressionModal({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [linkedinUrl, setLinkedinUrl] = useState("");
  const [email, setEmail] = useState("");
  const [reason, setReason] = useState<"manual" | "objected">("manual");
  const [note, setNote] = useState("");
  const [linkedinError, setLinkedinError] = useState<string | null>(null);

  const add = useMutation({
    mutationFn: () =>
      dataRetentionApi.addSuppression({
        linkedinUrl: linkedinUrl.trim() || null,
        email: email.trim() || null,
        reason,
        note: note.trim() || null,
      }),
    onSuccess: (added) => {
      void queryClient.invalidateQueries({ queryKey: dataRetentionApi.SUPPRESSIONS_KEY });
      toast(added.length === 2 ? "Added the profile and the email to the suppression list" : "Added to the suppression list");
      onClose();
    },
    onError: (error) => {
      const onProfile = error instanceof ApiRequestError ? error.fieldErrors.linkedinUrl : undefined;
      if (onProfile) setLinkedinError(onProfile);
      else toast.error(messageFor(error));
    },
  });

  const canAdd = linkedinUrl.trim() !== "" || email.trim() !== "";
  const handleAdd = () => {
    const problem = linkedinProfileProblem(linkedinUrl);
    setLinkedinError(problem);
    if (!problem && canAdd) add.mutate();
  };

  return (
    <Modal
      open
      onClose={onClose}
      dismissible={!add.isPending}
      title="Add to suppression list"
      subtitle="Nobody in your workspace can add this person again. Someone already on file stays until you delete them from Candidates."
      footer={
        <>
          <Button variant="secondary" disabled={add.isPending} onClick={onClose}>
            Cancel
          </Button>
          <Button disabled={!canAdd} loading={add.isPending} onClick={handleAdd}>
            Add to list
          </Button>
        </>
      }
    >
      <Field label="LinkedIn profile" error={linkedinError ?? undefined}>
        <Input
          value={linkedinUrl}
          invalid={linkedinError !== null}
          placeholder="linkedin.com/in/…"
          maxLength={500}
          onChange={(event) => {
            setLinkedinUrl(event.target.value);
            setLinkedinError(null);
          }}
        />
      </Field>
      <Field label="Email" hint="Give one or both. They are hashed when you save, and Uncava keeps nothing it could read back.">
        <Input
          type="email"
          value={email}
          placeholder="name@company.com"
          maxLength={320}
          onChange={(event) => setEmail(event.target.value)}
        />
      </Field>
      <div className="mb-4">
        <span className="mb-1.5 block type-micro-label font-mono text-u-text3">Reason</span>
        <ChoiceCardGroup label="Reason" options={REASON_OPTIONS} value={reason} onChange={setReason} />
      </div>
      <Field label={`Note · ${note.length} / ${NOTE_LIMIT}`}>
        <TextArea
          rows={3}
          value={note}
          maxLength={NOTE_LIMIT}
          placeholder="Who this is and why, for whoever reads the list later"
          onChange={(event) => setNote(event.target.value)}
        />
      </Field>
    </Modal>
  );
}
