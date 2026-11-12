import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { Field, FormError, TextArea, useToast } from "../../../../components/ui";
import { ConfirmDialog } from "../../../../components/ui/ConfirmDialog";
import { messageFor } from "../../../../lib/errorCodes";
import * as poolApi from "../../api/poolApi";
import type { PersonRecord } from "../../api/types";

const REASON_LIMIT = 500;

/**
 * Restrict processing (`Candidates.dc.html`'s dialog, GDPR Art. 18): what is on file stays, and nothing uses it until
 * someone on the team lifts it. The suppression list is offered, unticked — restricting is not yet an objection.
 */
export function RestrictProcessingDialog({
  person,
  onClose,
  onRestricted,
}: {
  person: PersonRecord;
  onClose: () => void;
  onRestricted: (record: PersonRecord) => void;
}) {
  const toast = useToast();
  const [reason, setReason] = useState("");
  const [suppress, setSuppress] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const restricting = useMutation({
    mutationFn: () => poolApi.setProcessingRestriction(person.personId, true, reason.trim(), suppress),
    onSuccess: (record) => {
      toast.success(suppress ? "Processing restricted, and added to the suppression list" : "Processing restricted");
      onRestricted(record);
    },
    onError: (failure) => setError(messageFor(failure)),
  });

  return (
    <ConfirmDialog
      open
      tone="primary"
      title={`Restrict processing for ${person.fullName}`}
      confirmLabel="Restrict"
      pending={restricting.isPending}
      confirmDisabled={!reason.trim()}
      onConfirm={() => restricting.mutate()}
      onClose={onClose}
    >
      <FormError message={error} />
      <p>
        Uncava keeps what&apos;s on file but stops using it: no AI, no research or contact lookups, no outreach, and
        nobody can add them to a new position. Use it while a request from them is open. Anyone on the team can lift
        it.
      </p>
      <Field
        label="Reason"
        action={<span className="text-meta text-u-text3">{`${reason.length} / ${REASON_LIMIT}`}</span>}
        hint="Shown on the drawer to everyone on the team, and kept in the audit log."
      >
        <TextArea
          value={reason}
          onChange={(event) => setReason(event.target.value.slice(0, REASON_LIMIT))}
          rows={3}
          maxLength={REASON_LIMIT}
          placeholder="e.g. Asked on 20 Sep what we hold about her; restricted until we reply"
          autoFocus
        />
      </Field>
      <label className="flex cursor-pointer items-start gap-2.5 rounded-u-card border border-u-border p-2.5">
        <input
          type="checkbox"
          checked={suppress}
          onChange={(event) => setSuppress(event.target.checked)}
          className="mt-0.5 accent-u-accent-solid"
        />
        <span className="flex flex-col">
          <span className="text-body font-medium text-u-text">
            Also add them to the suppression list, so no one can add them again
          </span>
          <span className="text-note text-u-text3">{suppressionLineOf(person)}</span>
        </span>
      </label>
    </ConfirmDialog>
  );
}

function suppressionLineOf(person: PersonRecord): string {
  const keys = [
    ...(person.linkedinUrl ? ["LinkedIn profile"] : []),
    ...(person.contacts.emails.length > 0 ? ["email addresses"] : []),
  ];
  if (keys.length === 0) return "There is no LinkedIn profile or email on file to match them on.";
  const verb = keys.length === 1 && !person.contacts.emails.length ? "is" : "are";
  return `Their ${keys.join(" and ")} ${verb} kept as one-way hashes. Every way in checks them first, and an admin can remove the entry in Settings.`;
}
