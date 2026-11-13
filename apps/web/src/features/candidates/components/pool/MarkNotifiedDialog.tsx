import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { Field, FormError, useToast } from "../../../../components/ui";
import { ConfirmDialog } from "../../../../components/ui/ConfirmDialog";
import { DateInput } from "../../../../components/ui/DateInput";
import { messageFor } from "../../../../lib/errorCodes";
import * as poolApi from "../../api/poolApi";
import type { PersonRecord } from "../../api/types";
import { todayIso } from "../../lib/candidatePrivacy";

/**
 * Mark as notified (#923, `Candidates.dc.html`'s `markNotified`): the person was told another way — a call, a meeting,
 * the firm's own email. Only the day is kept, never what was said.
 */
export function MarkNotifiedDialog({
  person,
  onClose,
  onMarked,
}: {
  person: PersonRecord;
  onClose: () => void;
  onMarked: (record: PersonRecord) => void;
}) {
  const toast = useToast();
  const today = todayIso();
  const [day, setDay] = useState(today);
  const [error, setError] = useState<string | null>(null);
  const marking = useMutation({
    mutationFn: () => poolApi.markNotified(person.personId, day),
    onSuccess: (record) => {
      toast.success("Marked as notified");
      onMarked(record);
    },
    onError: (failure) => setError(messageFor(failure)),
  });

  return (
    <ConfirmDialog
      open
      tone="primary"
      title={`Mark ${person.fullName} as notified`}
      confirmLabel="Mark as notified"
      pending={marking.isPending}
      confirmDisabled={!day || day > today}
      onConfirm={() => marking.mutate()}
      onClose={onClose}
    >
      <FormError message={error} />
      <p>
        Use this when you told them yourself that your firm holds their details, for example on a call or in your own
        email.
      </p>
      <Field label="When" hint="Today or earlier. Kept on their timeline and in the audit log.">
        <DateInput value={day} max={today} onChange={setDay} />
      </Field>
    </ConfirmDialog>
  );
}
