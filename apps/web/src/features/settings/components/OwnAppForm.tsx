import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { Button, DateInput, Field, FormError, Input } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { messageFor } from "../../../lib/errorCodes";
import type { WorkspaceIntegration } from "../api/types";
import { ownAppSchema, type OwnAppValues } from "../lib/ownAppSchema";
import { CopyableValue } from "./CopyableValue";

/**
 * The keys of a workspace's own app at one provider, with what its admin needs to register that app. Owns only
 * the form; saving is the card's, and a refusal thrown from there lands back on the field it names.
 */
export function OwnAppForm({
  integration,
  sharesWithRecall,
  canStoreKeys,
  onSave,
}: {
  integration: WorkspaceIntegration;
  /** The workspace syncs calendars through Recall, so this app's keys will be handed to Recall.ai. */
  sharesWithRecall: boolean;
  /** The deployment has a key to encrypt the secret with. */
  canStoreKeys: boolean;
  onSave: (values: OwnAppValues) => Promise<void>;
}) {
  const [formError, setFormError] = useState<string | null>(null);
  const needsTenant = integration.provider === "MICROSOFT";
  const storedSecretClientId = integration.mode === "OWN" && integration.secretSet ? integration.clientId : null;

  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<OwnAppValues>({
    resolver: zodResolver(ownAppSchema({ needsTenant, storedSecretClientId })),
    defaultValues: {
      clientId: integration.clientId ?? "",
      clientSecret: "",
      tenantId: integration.tenantId ?? "",
      secretExpiresOn: integration.secretExpiresAt?.slice(0, 10) ?? "",
    },
  });

  const submit = async (values: OwnAppValues) => {
    setFormError(null);
    try {
      await onSave(values);
    } catch (error) {
      const fieldErrors = error instanceof ApiRequestError ? error.fieldErrors : {};
      const fields = Object.keys(fieldErrors).filter((field): field is keyof OwnAppValues => field in values);
      if (fields.length > 0) {
        fields.forEach((field) => setError(field, { message: fieldErrors[field] }));
        return;
      }
      setFormError(messageFor(error));
    }
  };

  return (
    <form onSubmit={handleSubmit(submit)} noValidate className="space-y-4">
      <CopyableValue label="Redirect URI" value={integration.redirectUri} />

      {integration.scopes.length > 0 && (
        <div>
          <div className="mb-1.5 font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-u-text3">
            {needsTenant ? "Permissions to grant" : "Scopes to grant"}
          </div>
          <ul className="flex flex-wrap gap-1.5" aria-label="Scopes to grant">
            {integration.scopes.map((scope) => (
              <li key={scope}>
                <code className="rounded-[4px] bg-u-surface px-1.5 py-0.5 font-mono text-meta text-u-text2 [overflow-wrap:anywhere]">
                  {scope}
                </code>
              </li>
            ))}
          </ul>
        </div>
      )}

      <FormError message={formError} />

      <div className="grid grid-cols-1 gap-x-4 md:grid-cols-2">
        <Field label="Client ID" error={errors.clientId?.message}>
          <Input autoComplete="off" spellCheck={false} invalid={!!errors.clientId} {...register("clientId")} />
        </Field>
        {needsTenant && (
          <Field label="Tenant ID" error={errors.tenantId?.message} hint="Your directory's ID or primary domain">
            <Input autoComplete="off" spellCheck={false} invalid={!!errors.tenantId} {...register("tenantId")} />
          </Field>
        )}
        <Field
          label="Client secret"
          error={errors.clientSecret?.message}
          hint={sharesWithRecall ? "Shared with Recall.ai while calendar sync is on Recall" : undefined}
        >
          <Input
            type="password"
            autoComplete="new-password"
            spellCheck={false}
            placeholder={storedSecretClientId ? "Saved — leave blank to keep" : undefined}
            invalid={!!errors.clientSecret}
            {...register("clientSecret")}
          />
        </Field>
        <Field label="Secret expires" hint="From the provider's console, so you hear before it lapses">
          <Controller
            control={control}
            name="secretExpiresOn"
            render={({ field }) => (
              <DateInput value={field.value} onChange={field.onChange} ariaLabel="Secret expires" />
            )}
          />
        </Field>
      </div>

      {!canStoreKeys && (
        <p role="alert" className="font-mono text-[11.5px] text-u-offlimits">
          Your own app's keys can't be stored on this deployment yet. Use the shared app for now.
        </p>
      )}

      <div className="flex flex-wrap items-center justify-between gap-3">
        {integration.ownAppGuideUrl ? (
          <a
            href={integration.ownAppGuideUrl}
            target="_blank"
            rel="noreferrer"
            className="font-mono text-[11.5px] text-u-accent hover:underline"
          >
            How to register your own app ↗
          </a>
        ) : (
          <span />
        )}
        <Button type="submit" loading={isSubmitting} disabled={!canStoreKeys}>
          Save
        </Button>
      </div>
    </form>
  );
}
