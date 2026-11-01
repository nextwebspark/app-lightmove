import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Field, FormError, Input, useToast } from "../../../../components/ui";
import { ConfirmDialog } from "../../../../components/ui/ConfirmDialog";
import { confirmationNameMatches } from "../../../../lib/confirmationName";
import { codeOf, messageFor } from "../../../../lib/errorCodes";
import * as poolApi from "../../api/poolApi";
import type { PersonErasurePreview } from "../../api/types";

const SEND_IN_FLIGHT = "PERSON_ERASE_SEND_IN_FLIGHT";

/**
 * Deleting a person for good (`Candidates.dc.html`'s Delete person permanently), an admin's: what goes is read from
 * the server first, so the counts are the ones about to be deleted, and the name is typed back before the button
 * wakes. Refused while an email to them is sending, which would land after the delete.
 */
export function ErasePersonDialog({
  personId,
  fullName,
  onClose,
  onErased,
}: {
  personId: string;
  fullName: string;
  onClose: () => void;
  onErased: () => void;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [typed, setTyped] = useState("");
  const [suppress, setSuppress] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const preview = useQuery({
    queryKey: poolApi.ERASE_PREVIEW_KEY(personId),
    queryFn: ({ signal }) => poolApi.getErasePreview(personId, signal),
    staleTime: 0,
  });
  const erasing = useMutation({
    mutationFn: () => poolApi.erasePerson(personId, typed, suppress),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: poolApi.PERSON_RECORD_KEY(personId) });
      void queryClient.invalidateQueries({ queryKey: poolApi.POOL_KEY });
      toast.success(`${fullName} was deleted permanently`);
      onErased();
    },
    onError: (failure) => {
      if (codeOf(failure) === SEND_IN_FLIGHT) {
        setSending(true);
        setError(null);
        return;
      }
      setError(messageFor(failure));
    },
  });

  const isBlocked = sending || preview.data?.blockedBy === SEND_IN_FLIGHT;

  return (
    <ConfirmDialog
      open
      title={`Delete ${fullName} permanently?`}
      confirmLabel="Delete permanently"
      pending={erasing.isPending}
      confirmDisabled={preview.isPending || preview.isError || !confirmationNameMatches(fullName, typed)}
      onConfirm={() => erasing.mutate()}
      onClose={onClose}
    >
      <FormError message={error ?? (preview.isError ? messageFor(preview.error) : null)} />
      <p>
        Everything Uncava holds about them goes, on every position and for everyone in the workspace. This cannot be
        undone.
      </p>
      <span className="type-label mt-1 text-u-text3">What goes</span>
      {preview.data && <WhatGoes preview={preview.data} />}
      <p className="text-note text-u-text3">With them: their profile, contacts, tags, timeline and AI readings.</p>
      <label className="mt-1 flex cursor-pointer items-start gap-2.5 rounded-u-card border border-u-border p-2.5">
        <input
          type="checkbox"
          checked={suppress}
          onChange={(event) => setSuppress(event.target.checked)}
          className="mt-0.5 accent-u-accent-solid"
        />
        <span className="flex flex-col">
          <span className="text-body font-medium text-u-text">
            Add to the suppression list so they&apos;re never added again
          </span>
          <span className="text-note text-u-text3">
            Their LinkedIn profile and email addresses are kept as one-way hashes. Every way in checks them first, and
            an admin can remove the entry.
          </span>
        </span>
      </label>
      <p className="mt-1">
        Type <b className="font-semibold text-u-text">{fullName}</b> to confirm.
      </p>
      <Field label="Full name">
        <Input value={typed} onChange={(event) => setTyped(event.target.value)} autoComplete="off" autoFocus />
      </Field>
      {isBlocked && (
        <div
          role="alert"
          className="flex gap-2.5 rounded-u-card border border-u-offlimits/35 bg-u-offlimits-tint px-3 py-2 text-note text-u-text2"
        >
          <Icon d={ICONS.warning} size={14} className="mt-0.5 flex-none text-u-offlimits" />
          <span>
            <b className="font-semibold text-u-offlimits">An email to this person is sending right now. Try again in a
            minute.</b>{" "}
            Nothing was deleted.
          </span>
        </div>
      )}
      <p className="text-note text-u-text3">
        The audit log records the deletion, never their name. Copies in Uncava&apos;s backups are not restored and age
        out with them.
      </p>
    </ConfirmDialog>
  );
}

function WhatGoes({ preview }: { preview: PersonErasurePreview }) {
  const lines: Array<{ key: string; icon: string; count: number; label: string }> = [
    { key: "positions", icon: ICONS.position, count: preview.positions, label: plural(preview.positions, "position", "positions") },
    { key: "notes", icon: ICONS.noteBubble, count: preview.notes, label: plural(preview.notes, "note", "notes") },
    {
      key: "documents",
      icon: ICONS.fileText,
      count: preview.documents,
      label: `${plural(preview.documents, "document", "documents")} (${preview.files} ${plural(preview.files, "file", "files")})`,
    },
    {
      key: "emails",
      icon: ICONS.mail,
      count: preview.emails,
      label: `${plural(preview.emails, "email", "emails")} and ${preview.outreachRuns} outreach ${plural(preview.outreachRuns, "run", "runs")}`,
    },
    { key: "meetings", icon: ICONS.calendar, count: preview.meetings, label: plural(preview.meetings, "meeting", "meetings") },
    {
      key: "recordings",
      icon: ICONS.mic,
      count: preview.recordings,
      label: plural(preview.recordings, "call recording", "call recordings"),
    },
    { key: "tasks", icon: ICONS.checklist, count: preview.tasks, label: plural(preview.tasks, "task", "tasks") },
  ];
  return (
    <ul aria-label="What goes" className="grid grid-cols-1 gap-1.5 sm:grid-cols-2">
      {lines.map((line) => (
        <li key={line.key} className="flex items-center gap-2 rounded-u-control bg-u-raised px-2.5 py-1.5 text-note">
          <Icon d={line.icon} size={13} className="flex-none text-u-text3" />
          <span>
            <b className="font-semibold text-u-text">{line.count}</b> {line.label}
          </span>
        </li>
      ))}
    </ul>
  );
}

function plural(count: number, one: string, many: string): string {
  return count === 1 ? one : many;
}
