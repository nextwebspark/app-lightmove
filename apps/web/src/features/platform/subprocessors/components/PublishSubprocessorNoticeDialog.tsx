import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { z } from "zod";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Button, DateInput, Field, FormError, Input, Modal } from "../../../../components/ui";
import { codeOf } from "../../../../lib/errorCodes";
import { fieldErrorsFrom } from "../../../../lib/formErrors";
import * as subprocessorApi from "../api/subprocessorApi";
import type { SubprocessorNotice } from "../api/types";
import {
  EFFECTIVE_ON_TOO_SOON,
  earliestEffectiveOn,
  isHttpsLink,
  LINK_NOT_HTTPS,
  NOTICE_LIMITS,
} from "../lib/subprocessorLabels";

const required = (limit: number) => z.string().trim().min(1, "Required").max(limit, `At most ${limit} characters`);

const noticeSchema = z.object({
  provider: required(NOTICE_LIMITS.provider),
  region: required(NOTICE_LIMITS.region),
  receives: required(NOTICE_LIMITS.receives),
  purpose: required(NOTICE_LIMITS.purpose),
  effectiveOn: z.string().min(1, "Required").refine((day) => day >= earliestEffectiveOn(), EFFECTIVE_ON_TOO_SOON),
  listUrl: required(NOTICE_LIMITS.listUrl).refine(isHttpsLink, LINK_NOT_HTTPS),
});
type NoticeValues = z.infer<typeof noticeSchema>;
type NoticeField = keyof NoticeValues;

const EMPTY: NoticeValues = { provider: "", region: "", receives: "", purpose: "", effectiveOn: "", listUrl: "" };
const FIELDS: readonly NoticeField[] = ["provider", "region", "receives", "purpose", "effectiveOn", "listUrl"];

/**
 * Publish a sub-processor change (`PlatformDialogs.dc.html`, `subprocessorPublish`): at least 30 days ahead, and on
 * Publish every workspace admin is emailed. The server asks for a fresh two-factor code first.
 */
export function PublishSubprocessorNoticeDialog({
  onClose,
  onPublished,
}: {
  onClose: () => void;
  onPublished: (notice: SubprocessorNotice) => void;
}) {
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    control,
    handleSubmit,
    setError,
    watch,
    formState: { errors },
  } = useForm<NoticeValues>({ resolver: zodResolver(noticeSchema), defaultValues: EMPTY });
  const values = watch();
  const isBlank = FIELDS.some((field) => !values[field].trim());

  const publish = useMutation({
    mutationFn: (notice: NoticeValues) => subprocessorApi.publishNotice(notice),
    onSuccess: onPublished,
    onError: (error) => {
      const { fields, formMessage } = fieldErrorsFrom<NoticeField>(error, {
        provider: "provider",
        region: "region",
        receives: "receives",
        purpose: "purpose",
        effectiveOn: "effectiveOn",
        listUrl: "listUrl",
      });
      for (const field of FIELDS) {
        const message = fields[field];
        if (message) setError(field, { type: "server", message });
      }
      // The code dialog was closed without a code: nothing was published, and saying so again adds nothing.
      setFormError(codeOf(error) === "PLATFORM_STEP_UP_REQUIRED" ? null : formMessage);
    },
  });

  const submit = handleSubmit((notice) => {
    setFormError(null);
    publish.mutate({
      provider: notice.provider.trim(),
      region: notice.region.trim(),
      receives: notice.receives.trim(),
      purpose: notice.purpose.trim(),
      effectiveOn: notice.effectiveOn,
      listUrl: notice.listUrl.trim(),
    });
  });

  return (
    <Modal
      open
      onClose={onClose}
      onSubmit={() => void submit()}
      title="Publish a sub-processor change"
      subtitle="A new provider that will handle workspaces' data. Admins can object until it takes effect."
      footer={
        <>
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={publish.isPending} disabled={isBlank}>
            Publish
          </Button>
        </>
      }
    >
      <FormError message={formError} />
      <div className="grid grid-cols-1 gap-x-3.5 md:grid-cols-2">
        <Field label="Provider" error={errors.provider?.message}>
          <Input maxLength={NOTICE_LIMITS.provider} invalid={!!errors.provider} {...register("provider")} />
        </Field>
        <Field label="Region" error={errors.region?.message}>
          <Input maxLength={NOTICE_LIMITS.region} invalid={!!errors.region} {...register("region")} />
        </Field>
        <div className="md:col-span-2">
          <Field label="What it receives" error={errors.receives?.message}>
            <Input maxLength={NOTICE_LIMITS.receives} invalid={!!errors.receives} {...register("receives")} />
          </Field>
        </div>
        <div className="md:col-span-2">
          <Field label="Purpose" error={errors.purpose?.message}>
            <Input maxLength={NOTICE_LIMITS.purpose} invalid={!!errors.purpose} {...register("purpose")} />
          </Field>
        </div>
        <Field label="Takes effect" error={errors.effectiveOn?.message} hint="At least 30 days from today">
          <Controller
            control={control}
            name="effectiveOn"
            render={({ field }) => (
              <DateInput value={field.value} onChange={field.onChange} min={earliestEffectiveOn()} ariaLabel="Takes effect" />
            )}
          />
        </Field>
        <Field label="Link to the published list" error={errors.listUrl?.message} hint="The page on uncava.com that lists it">
          <Input
            type="url"
            maxLength={NOTICE_LIMITS.listUrl}
            placeholder="https://uncava.com/…"
            invalid={!!errors.listUrl}
            {...register("listUrl")}
          />
        </Field>
      </div>
      <div className="mt-1 flex gap-2.5 rounded-lg border border-u-border bg-u-sunken px-3 py-2.5 text-note text-u-text2">
        <Icon d={ICONS.mail} size={15} className="mt-0.5 flex-none text-u-text3" />
        <span>Emails every workspace admin now.</span>
      </div>
    </Modal>
  );
}
