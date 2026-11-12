import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState, type ReactNode } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import { Button, DateInput, Field, FormError, Input } from "../../../components/ui";
import type { WorkspaceIntegration } from "../api/types";
import { connectionsLine } from "../lib/integrationConnections";
import { INTEGRATION_PROVIDERS } from "../lib/integrationProviders";
import { failureOf, type SaveFailure } from "../lib/ownAppSaveFailure";
import { isoToday, ownAppSchema, type OwnAppValues } from "../lib/ownAppSchema";
import { CopyableValue, CopyButton } from "./CopyableValue";
import { KeysRefusedAlert, RecallKeysNote, ScopeCodes } from "./OwnAppParts";
import { SecretInput } from "./SecretInput";

/**
 * Setting up an own app in the order the provider's console asks for it, under the console's own field names. Owns
 * only the form: saving is the card's, and a refusal thrown from there lands back here.
 */
export function OwnAppSetupForm({
  integration,
  canStoreKeys,
  sharesWithRecall,
  onSave,
  onCancel,
}: {
  integration: WorkspaceIntegration;
  /** The workspace has a key to encrypt the secret with. */
  canStoreKeys: boolean;
  sharesWithRecall: boolean;
  onSave: (values: OwnAppValues) => Promise<void>;
  onCancel: () => void;
}) {
  const copy = INTEGRATION_PROVIDERS[integration.provider];
  const isMicrosoft = integration.provider === "MICROSOFT";
  const [failure, setFailure] = useState<SaveFailure | null>(null);

  const {
    register,
    control,
    handleSubmit,
    setError,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<OwnAppValues>({
    resolver: zodResolver(ownAppSchema({ provider: integration.provider, today: isoToday() })),
    defaultValues: { clientId: "", clientSecret: "", tenantId: "", secretExpiresOn: "" },
  });

  useEffect(() => {
    const subscription = watch(() => setFailure(null));
    return () => subscription.unsubscribe();
  }, [watch]);

  const typedClientId = useWatch({ control, name: "clientId" }).trim();
  const appInUse = integration.mode === "OWN" ? integration.clientId : null;
  const leavesTheAppInUse = integration.connectionsThroughApp > 0 && typedClientId !== appInUse;

  const submit = async (values: OwnAppValues) => {
    setFailure(null);
    try {
      await onSave(values);
    } catch (error) {
      const failed = failureOf(error, values, (field, message) => setError(field, { message }));
      if (failed) setFailure(failed);
    }
  };

  return (
    <form onSubmit={handleSubmit(submit)} noValidate>
      {leavesTheAppInUse && (
        <p role="status" className="mb-3.5 rounded-u-chip bg-u-signal-tint px-3.5 py-2.5 text-note text-u-text">
          <b className="font-semibold text-u-signal">
            {connectionsLine(integration.provider, integration.connectionsThroughApp)}
          </b>{" "}
          Saving a different app asks each of them to connect again, once.
        </p>
      )}

      <ol className="flex flex-col gap-4.5">
        <SetupStep number={1} title={`Register an app in ${copy.console}`}>
          <p className="mt-1 font-mono text-meta text-u-text3">
            {copy.registerHint}{" "}
            {integration.ownAppGuideUrl && (
              <a
                href={integration.ownAppGuideUrl}
                target="_blank"
                rel="noreferrer"
                className="text-u-accent hover:underline"
              >
                How to register your own app ↗
              </a>
            )}
          </p>
        </SetupStep>

        <SetupStep number={2} title="Add this redirect URI">
          <div className="mt-1.5">
            <CopyableValue label="Redirect URI" value={integration.redirectUri} showLabel={false} />
          </div>
        </SetupStep>

        <SetupStep
          number={3}
          title={`Grant these ${copy.scopesNoun}`}
          action={
            integration.scopes.length > 0 && (
              <CopyButton
                value={integration.scopes.join(" ")}
                what={copy.scopesLabel}
                label={`Copy all ${copy.scopesNoun}`}
                className="rounded-u-control px-2 py-1 text-note font-medium text-u-accent transition hover:bg-u-accent-tint"
              >
                Copy all
              </CopyButton>
            )
          }
        >
          <div className="mt-1.5">
            <ScopeCodes scopes={integration.scopes} label={copy.scopesNoun} />
          </div>
        </SetupStep>

        <SetupStep number={4} title="Paste its keys">
          <div className="mt-2.5">
            {failure && "refusedWith" in failure && (
              <div className="mb-3">
                <KeysRefusedAlert
                  short={copy.short}
                  providerError={failure.refusedWith}
                  consequence="Nothing was saved."
                />
              </div>
            )}
            <FormError message={failure && "message" in failure ? failure.message : null} />

            <div className="grid grid-cols-1 gap-x-3.5 md:grid-cols-2">
              <Field
                label={copy.idLabel}
                error={errors.clientId?.message}
                hint={integration.provider === "GOOGLE" ? "Ends in .apps.googleusercontent.com" : undefined}
              >
                <Input autoComplete="off" spellCheck={false} invalid={!!errors.clientId} {...register("clientId")} />
              </Field>
              {isMicrosoft && (
                <Field
                  label="Directory (tenant) ID"
                  error={errors.tenantId?.message}
                  hint="Your directory's ID or primary domain, from the app's Overview."
                >
                  <Input autoComplete="off" spellCheck={false} invalid={!!errors.tenantId} {...register("tenantId")} />
                </Field>
              )}
              <SecretInput
                label={copy.secretLabel}
                error={errors.clientSecret?.message}
                hint={copy.onceNote}
                {...register("clientSecret")}
              />
              {isMicrosoft && (
                <Field
                  label="Secret expires"
                  error={errors.secretExpiresOn?.message}
                  hint="The Expires column in Certificates & secrets. Entra allows 24 months at most; we email you 30 and 7 days before."
                >
                  <Controller
                    control={control}
                    name="secretExpiresOn"
                    render={({ field }) => (
                      <DateInput value={field.value} onChange={field.onChange} ariaLabel="Secret expires" />
                    )}
                  />
                </Field>
              )}
            </div>
            {sharesWithRecall && <RecallKeysNote />}
          </div>
        </SetupStep>
      </ol>

      {!canStoreKeys && (
        <p role="alert" className="mt-4 font-mono text-meta text-u-offlimits">
          Your own app's keys can't be stored for your workspace yet. Use the shared app for now, or contact Uncava
          support.
        </p>
      )}

      <div className="mt-4.5 flex flex-wrap justify-end gap-2">
        <Button variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
        <Button type="submit" loading={isSubmitting} disabled={!canStoreKeys}>
          {isSubmitting ? `Checking with ${copy.short}…` : "Save and verify"}
        </Button>
      </div>
    </form>
  );
}

function SetupStep({
  number,
  title,
  action,
  children,
}: {
  number: number;
  title: string;
  action?: ReactNode;
  children: ReactNode;
}) {
  return (
    <li className="flex gap-3">
      <span
        aria-hidden="true"
        className="grid size-5.5 flex-none place-items-center rounded-full border border-u-border bg-u-surface font-mono text-meta font-semibold text-u-text2"
      >
        {number}
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex items-center justify-between gap-2">
          <span className="type-heading">{title}</span>
          {action}
        </div>
        {children}
      </div>
    </li>
  );
}
