import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Button, Field, FormError, Modal, TextArea } from "../../../../components/ui";
import { codeOf } from "../../../../lib/errorCodes";
import { fieldErrorsFrom } from "../../../../lib/formErrors";
import * as variableApi from "../api/variableApi";
import type { ApplicationVariable } from "../api/types";
import { NOTE_LIMIT, valueInWords } from "../lib/variableLabels";

const resetSchema = z.object({
  note: z.string().trim().min(1, "Say why, for whoever reads the history later.").max(NOTE_LIMIT, `At most ${NOTE_LIMIT} characters`),
});
type ResetValues = z.infer<typeof resetSchema>;

/** Reset to default: asked once more, with a note. The server asks for a fresh two-factor code, as for a change. */
export function ResetVariableDialog({
  variable,
  onClose,
  onReset,
}: {
  variable: ApplicationVariable;
  onClose: () => void;
  onReset: (updated: ApplicationVariable) => void;
}) {
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isValid },
  } = useForm<ResetValues>({ resolver: zodResolver(resetSchema), mode: "onChange", defaultValues: { note: "" } });

  const reset = useMutation({
    mutationFn: ({ note }: ResetValues) => variableApi.resetVariable(variable.key, note),
    onSuccess: onReset,
    onError: (error) => {
      const { fields, formMessage } = fieldErrorsFrom<keyof ResetValues>(error, { note: "note" });
      if (fields.note) setError("note", { type: "server", message: fields.note });
      // The code dialog was closed without a code: nothing happened, and saying so again adds nothing.
      setFormError(codeOf(error) === "PLATFORM_STEP_UP_REQUIRED" ? null : formMessage);
    },
  });

  const submit = handleSubmit((values) => {
    setFormError(null);
    reset.mutate(values);
  });

  return (
    <Modal
      open
      onClose={onClose}
      onSubmit={() => void submit()}
      title={`Reset ${variable.label} to its default?`}
      footer={
        <>
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={reset.isPending} disabled={!isValid}>
            Reset to default
          </Button>
        </>
      }
    >
      <p className="mb-4 text-body text-u-text2">
        It goes back from {variable.value} to {valueInWords(variable.defaultValue, variable.unit)} on every instance,
        within seconds. The change stays in the history.
      </p>
      <FormError message={formError} />
      <Field label="Change note" error={errors.note?.message}>
        <TextArea
          rows={2}
          maxLength={NOTE_LIMIT}
          autoFocus
          placeholder="Why it goes back to the default"
          invalid={!!errors.note}
          {...register("note")}
        />
      </Field>
    </Modal>
  );
}
