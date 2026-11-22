import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Button, Field, FormError, Modal, TextArea } from "../../../../components/ui";
import { codeOf, messageFor } from "../../../../lib/errorCodes";
import * as promptApi from "../api/promptApi";
import type { PromptDetail, PromptVersionSummary } from "../api/types";
import { versionLabel } from "../lib/promptLabels";

const REASON_LIMIT = 500;

const putLiveSchema = z.object({
  reason: z
    .string()
    .trim()
    .min(1, "Say why, for whoever reviews this later")
    .max(REASON_LIMIT, `At most ${REASON_LIMIT} characters`),
});
type PutLiveValues = z.infer<typeof putLiveSchema>;

/**
 * Put a version live, or roll back to an earlier one: the one act here that changes what every workspace's AI
 * calls are answered with. The server asks for a fresh two-factor code, which the app's own dialog collects.
 */
export function PutLiveDialog({
  detail,
  target,
  onClose,
  onDone,
}: {
  detail: PromptDetail;
  target: PromptVersionSummary;
  onClose: () => void;
  onDone: (updated: PromptDetail) => void;
}) {
  const live = detail.versions.find((version) => version.live);
  const isRollback = target.version < detail.prompt.liveVersion;
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isValid },
  } = useForm<PutLiveValues>({ resolver: zodResolver(putLiveSchema), mode: "onChange", defaultValues: { reason: "" } });

  const move = useMutation({
    mutationFn: (reason: string) => promptApi.putPromptLive(detail.prompt.id, target.version, reason),
    onSuccess: onDone,
    onError: (error) => {
      // The code dialog was closed without a code: nothing happened, and saying so again adds nothing.
      setFormError(codeOf(error) === "PLATFORM_STEP_UP_REQUIRED" ? null : messageFor(error));
    },
  });

  const submit = handleSubmit(({ reason }) => {
    setFormError(null);
    move.mutate(reason.trim());
  });
  const targetModel = target.modelName ?? "Default model";
  const liveModel = live?.modelName ?? "Default model";

  return (
    <Modal
      open
      onClose={onClose}
      onSubmit={() => void submit()}
      title={isRollback ? `Roll back to ${versionLabel(target.version)}` : `Put ${versionLabel(target.version)} live`}
      footer={
        <>
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={move.isPending} disabled={!isValid}>
            {isRollback ? "Roll back" : "Put live"}
          </Button>
        </>
      }
    >
      <p className="mb-4 text-body text-u-text2">
        {detail.prompt.name} goes from {versionLabel(detail.prompt.liveVersion)} to {versionLabel(target.version)} for
        every workspace, within seconds.
      </p>
      {targetModel !== liveModel && (
        <div className="mb-4 flex flex-wrap items-center gap-2 rounded-lg border border-u-border px-3 py-2.5 text-body">
          <span className="type-micro-label font-mono text-u-text3">Model</span>
          <span className="text-u-text2">{liveModel}</span>
          <span aria-hidden="true" className="text-u-text3">
            →
          </span>
          <span className="text-u-text">{targetModel}</span>
        </div>
      )}
      <FormError message={formError} />
      <Field label={isRollback ? "Why roll back" : "Why put it live anyway"} error={errors.reason?.message}>
        <TextArea
          rows={3}
          maxLength={REASON_LIMIT}
          autoFocus
          placeholder="Recorded with your name, for whoever reviews this later"
          invalid={!!errors.reason}
          {...register("reason")}
        />
      </Field>
      {detail.aiUse && (
        <p className="text-xs text-u-text3">
          If this changes what the feature records about a person, its description in Settings → Data &amp; retention
          needs updating in the next release.
        </p>
      )}
    </Modal>
  );
}
