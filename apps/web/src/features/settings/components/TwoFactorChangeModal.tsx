import { zodResolver } from "@hookform/resolvers/zod";
import { useState, type ReactNode } from "react";
import { useForm } from "react-hook-form";
import { Button, Field, FormError, Input, Modal } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { messageFor } from "../../../lib/errorCodes";
import { twoFactorChangeSchema, twoFactorEnableSchema, type TwoFactorChangeValues } from "../lib/twoFactorSchema";

/**
 * The current password and a code, the two things every two-factor change asks for. Owns the form alone: what the
 * change does is the caller's `onConfirm`, whose refusal lands back here on the field it names.
 */
export function TwoFactorChangeModal({
  title,
  intro,
  confirmLabel,
  tone = "primary",
  authenticatorOnly = false,
  onConfirm,
  onClose,
  children,
}: {
  title: string;
  intro: string;
  confirmLabel: string;
  tone?: "primary" | "danger";
  /** Turning it on: only the app's code proves the app holds the new secret. */
  authenticatorOnly?: boolean;
  onConfirm: (values: TwoFactorChangeValues) => Promise<void>;
  onClose: () => void;
  /** Drawn between the intro and the fields: turning it on shows the QR code here. */
  children?: ReactNode;
}) {
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<TwoFactorChangeValues>({
    resolver: zodResolver(authenticatorOnly ? twoFactorEnableSchema : twoFactorChangeSchema),
    defaultValues: { currentPassword: "", code: "" },
  });

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onConfirm({ currentPassword: values.currentPassword, code: values.code.trim() });
    } catch (error) {
      if (!(error instanceof ApiRequestError)) {
        setFormError("Could not reach Uncava. Check your connection and try again.");
        return;
      }
      const fieldErrors = error.fieldErrors;
      const fields = Object.keys(fieldErrors) as (keyof TwoFactorChangeValues)[];
      if (fields.length > 0) {
        fields.forEach((field) => setError(field, { message: fieldErrors[field] }));
        return;
      }
      setFormError(messageFor(error));
    }
  });

  return (
    <Modal
      open
      onClose={onClose}
      onSubmit={() => void submit()}
      title={title}
      className="md:w-[440px]"
      footer={
        <>
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" variant={tone} loading={isSubmitting}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      <p className="mb-4 text-body text-u-text2">{intro}</p>
      {children}
      <FormError message={formError} />
      <Field label="Current password" error={errors.currentPassword?.message}>
        <Input
          type="password"
          autoComplete="current-password"
          placeholder="••••••••"
          invalid={!!errors.currentPassword}
          {...register("currentPassword")}
        />
      </Field>
      <Field
        label={authenticatorOnly ? "Code from your app" : "Code from your app, or a recovery code"}
        error={errors.code?.message}
      >
        <Input
          inputMode={authenticatorOnly ? "numeric" : "text"}
          autoComplete="one-time-code"
          spellCheck={false}
          placeholder={authenticatorOnly ? "123456" : "123456 or ABCD-EFGH-IJKL-MNOP"}
          invalid={!!errors.code}
          {...register("code")}
        />
      </Field>
    </Modal>
  );
}
