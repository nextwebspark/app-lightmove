import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Button, Field, FormError, Input, Modal } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { messageFor } from "../../../lib/errorCodes";
import { secondFactorCodeRule } from "../../auth/schemas";
import * as platformApi from "../api/platformApi";

const stepUpSchema = z.object({ code: secondFactorCodeRule });
type StepUpValues = z.infer<typeof stepUpSchema>;

/** Confirm it's you: the code before a change that reaches every workspace. A wrong one stays on its field. */
export default function PlatformStepUpDialog({ onConfirmed, onClose }: { onConfirmed: () => void; onClose: () => void }) {
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<StepUpValues>({ resolver: zodResolver(stepUpSchema), defaultValues: { code: "" } });

  const submit = handleSubmit(async ({ code }) => {
    setFormError(null);
    try {
      await platformApi.confirmStepUp(code.trim());
      onConfirmed();
    } catch (error) {
      if (!(error instanceof ApiRequestError)) {
        setFormError("Could not reach Uncava. Check your connection and try again.");
        return;
      }
      if (error.fieldErrors.code) {
        setError("code", { message: error.fieldErrors.code });
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
      title="Confirm it's you"
      className="md:w-[400px]"
      footer={
        <>
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={isSubmitting}>
            Confirm
          </Button>
        </>
      }
    >
      <p className="mb-4 text-body text-u-text2">
        This change reaches every workspace. Enter the 6-digit code from your authenticator app to go on. You won't be
        asked again for a short while.
      </p>
      <FormError message={formError} />
      <Field label="Code" error={errors.code?.message} hint="Lost your phone? Enter a recovery code instead.">
        <Input
          inputMode="text"
          autoComplete="one-time-code"
          spellCheck={false}
          autoFocus
          placeholder="000 000"
          invalid={!!errors.code}
          {...register("code")}
        />
      </Field>
    </Modal>
  );
}
