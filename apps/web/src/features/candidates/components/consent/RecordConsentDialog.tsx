import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Field, FormError, SegmentedControl, Select, useToast } from "../../../../components/ui";
import { ConfirmDialog } from "../../../../components/ui/ConfirmDialog";
import { DateInput } from "../../../../components/ui/DateInput";
import { formatInstantDate } from "../../../../lib/datetime";
import { messageFor } from "../../../../lib/errorCodes";
import * as poolApi from "../../api/poolApi";
import type { ConsentAnswer, PersonRecord } from "../../api/types";
import { todayIso } from "../../lib/candidatePrivacy";
import type { PersonalPurpose } from "../../lib/consentPreview";

const PURPOSES: readonly { value: PersonalPurpose; label: string }[] = [
  { value: "keep_on_file", label: "Keep on file for future roles" },
  { value: "marketing", label: "Career and market updates" },
];

const ANSWERS = [
  { value: "given", label: "Given" },
  { value: "declined", label: "Declined" },
  { value: "withdrawn", label: "Withdrawn" },
] as const;

function validUntilOf(answeredOn: string, months: number): string | null {
  const [year, month, dayOfMonth] = answeredOn.split("-").map(Number);
  if (!year || !month || !dayOfMonth) return null;
  return formatInstantDate(new Date(Date.UTC(year, month - 1 + months, dayOfMonth, 12)).toISOString());
}

/**
 * Record an answer (#1267, `Candidates.dc.html`'s `recordConsent`): the person answered another way, on a call or in
 * their own email. Only the purpose, the answer and the day are kept, marked as recorded by hand.
 */
export function RecordConsentDialog({
  person,
  keepOnFileMonths,
  onClose,
  onRecorded,
}: {
  person: PersonRecord;
  keepOnFileMonths: number;
  onClose: () => void;
  onRecorded: (record: PersonRecord) => void;
}) {
  const toast = useToast();
  const today = todayIso();
  const [purpose, setPurpose] = useState<PersonalPurpose>("keep_on_file");
  const [answer, setAnswer] = useState<ConsentAnswer>("given");
  const [answeredOn, setAnsweredOn] = useState(today);
  const [error, setError] = useState<string | null>(null);
  const recording = useMutation({
    mutationFn: () => poolApi.recordConsent(person.personId, { purpose, answer, answeredOn }),
    onSuccess: (record) => {
      toast.success("Answer recorded");
      onRecorded(record);
    },
    onError: (failure) => setError(messageFor(failure)),
  });

  const until = purpose === "keep_on_file" && answer === "given" && answeredOn
    ? validUntilOf(answeredOn, keepOnFileMonths)
    : null;

  return (
    <ConfirmDialog
      open
      tone="primary"
      title={`Record an answer from ${person.fullName}`}
      confirmLabel="Record answer"
      pending={recording.isPending}
      confirmDisabled={!answeredOn || answeredOn > today}
      onConfirm={() => recording.mutate()}
      onClose={onClose}
    >
      <FormError message={error} />
      <p>Use this when they answered you directly, for example on a call or in their own email.</p>
      <Field label="Purpose">
        <Select
          density="compact"
          className="w-auto"
          value={purpose}
          onChange={(event) => setPurpose(event.target.value as PersonalPurpose)}
        >
          {PURPOSES.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </Select>
      </Field>
      <div className="mb-3.5">
        <div className="mb-1.5 text-note font-medium text-u-text2">Answer</div>
        <SegmentedControl label="Answer" options={ANSWERS} value={answer} onChange={setAnswer} />
      </div>
      <Field label="On" hint={until ? `Today or earlier. Valid until ${until}.` : "Today or earlier."}>
        <DateInput value={answeredOn} max={today} onChange={setAnsweredOn} />
      </Field>
      <div className="flex gap-2.5 rounded-u-chip border border-u-border bg-u-raised px-3 py-2.5 font-mono text-meta text-u-text2">
        <Icon d={ICONS.info} size={15} className="mt-px flex-none text-u-text3" />
        <span>Kept on their timeline and in the audit log, marked as recorded by hand.</span>
      </div>
    </ConfirmDialog>
  );
}
