import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { FormError, useToast } from "../../../../components/ui";
import { ConfirmDialog } from "../../../../components/ui/ConfirmDialog";
import { messageFor } from "../../../../lib/errorCodes";
import * as poolApi from "../../api/poolApi";
import type { PersonRecord } from "../../api/types";

/**
 * Do not contact as the person's objection (B16, `CandidatesRestrictEraseDialogs.dc.html`): the same stop as the link
 * in our emails, so it asks first — every run stops now and the suppression list holds them until an admin lifts it.
 */
export function RecordObjectionDialog({
  person,
  onClose,
  onRecorded,
}: {
  person: PersonRecord;
  onClose: () => void;
  onRecorded: (record: PersonRecord) => void;
}) {
  const toast = useToast();
  const [error, setError] = useState<string | null>(null);
  const recording = useMutation({
    mutationFn: () => poolApi.setDoNotContact(person.personId, true),
    onSuccess: (record) => {
      toast.success("Marked do not contact. Their emails have stopped and your admins have been told.");
      onRecorded(record);
    },
    onError: (failure) => setError(messageFor(failure)),
  });

  return (
    <ConfirmDialog
      open
      title={`Mark ${person.fullName} do not contact?`}
      confirmLabel="Mark do not contact"
      pending={recording.isPending}
      onConfirm={() => recording.mutate()}
      onClose={onClose}
    >
      <FormError message={error} />
      <p>
        Use this when they&apos;ve asked us to stop — in a reply, on a call or in person. It works like the stop link in
        our emails: every sequence and scheduled email to them stops now, they join the suppression list so no one can
        add them again, and your workspace admins are told.
      </p>
      <p className="font-mono text-meta text-u-text3">
        Only an admin can undo this, by removing them from the suppression list in Settings → Data &amp; retention. The
        audit log records who marked it.
      </p>
    </ConfirmDialog>
  );
}
