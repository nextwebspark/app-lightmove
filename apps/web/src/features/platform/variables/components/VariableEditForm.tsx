import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Button, Field, FormError, Input, TextArea } from "../../../../components/ui";
import { codeOf } from "../../../../lib/errorCodes";
import { fieldErrorsFrom } from "../../../../lib/formErrors";
import * as variableApi from "../api/variableApi";
import type { ApplicationVariable } from "../api/types";
import { NOTE_LIMIT, outOfRangeMessage, rangeHint } from "../lib/variableLabels";

const WHOLE_NUMBER = /^\d+$/;

const editSchema = (variable: ApplicationVariable) =>
  z.object({
    value: z
      .string()
      .trim()
      .refine((typed) => {
        const number = Number(typed);
        return WHOLE_NUMBER.test(typed) && number >= variable.min && number <= variable.max;
      }, outOfRangeMessage(variable)),
    note: z
      .string()
      .trim()
      .min(1, "Say why, for whoever reads the history later.")
      .max(NOTE_LIMIT, `At most ${NOTE_LIMIT} characters`),
  });
type EditValues = z.infer<ReturnType<typeof editSchema>>;

/**
 * A variable's new value and why, in its own row. The server holds the same range and answers on the same fields;
 * it also asks for a fresh two-factor code, which the app's own dialog collects.
 */
export function VariableEditForm({
  variable,
  onCancel,
  onSaved,
}: {
  variable: ApplicationVariable;
  onCancel: () => void;
  onSaved: (updated: ApplicationVariable) => void;
}) {
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<EditValues>({
    resolver: zodResolver(editSchema(variable)),
    defaultValues: { value: `${variable.value}`, note: "" },
  });

  const save = useMutation({
    mutationFn: ({ value, note }: EditValues) => variableApi.changeVariable(variable.key, value, note),
    onSuccess: onSaved,
    onError: (error) => {
      const { fields, formMessage } = fieldErrorsFrom<keyof EditValues>(error, { value: "value", note: "note" });
      if (fields.value) setError("value", { type: "server", message: fields.value });
      if (fields.note) setError("note", { type: "server", message: fields.note });
      // The code dialog was closed without a code: nothing happened, and saying so again adds nothing.
      setFormError(codeOf(error) === "PLATFORM_STEP_UP_REQUIRED" ? null : formMessage);
    },
  });

  const submit = handleSubmit((values) => {
    setFormError(null);
    save.mutate(values);
  });

  return (
    <form
      noValidate
      onSubmit={(event) => void submit(event)}
      className="mt-3 rounded-lg border border-u-border-strong bg-u-surface p-3.5"
    >
      <FormError message={formError} />
      <div className="flex flex-wrap gap-x-4">
        <div className="flex-none">
          <Field label="New value" error={errors.value?.message} hint={rangeHint(variable)}>
            <span className="flex items-center gap-2">
              <Input
                inputMode="numeric"
                autoFocus
                aria-label={`New value of ${variable.label}`}
                invalid={!!errors.value}
                className="w-[120px] font-u-num"
                {...register("value")}
              />
              {variable.unit && <span className="text-xs text-u-text3">{variable.unit}</span>}
            </span>
          </Field>
        </div>
        <div className="min-w-0 flex-[1_1_280px]">
          <Field label="Change note" error={errors.note?.message}>
            <TextArea
              rows={2}
              maxLength={NOTE_LIMIT}
              placeholder="Why this value, for whoever reads the history later"
              invalid={!!errors.note}
              {...register("note")}
            />
          </Field>
        </div>
      </div>
      <div className="flex flex-wrap justify-end gap-2">
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>
          Cancel
        </Button>
        <Button type="submit" size="sm" loading={save.isPending}>
          Save
        </Button>
      </div>
    </form>
  );
}
