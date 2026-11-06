import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useId, useState, type ReactNode } from "react";
import { Controller, useForm } from "react-hook-form";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, DateInput, Field, FormError, Spinner, useToast } from "../../../components/ui";
import { FoldBody } from "../../../components/ui/FoldBody";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { formatDate, formatInstantDate, formatTimeAgo } from "../../../lib/format";
import * as integrationsApi from "../api/integrationsApi";
import type { WorkspaceIntegration } from "../api/types";
import { connectionsLine } from "../lib/integrationConnections";
import { INTEGRATION_PROVIDERS, type IntegrationProviderCopy } from "../lib/integrationProviders";
import { dayCount, EXPIRY_WARNING_DAYS, secretDaysLeft } from "../lib/integrationStatus";
import { failureOf, type SaveFailure } from "../lib/ownAppSaveFailure";
import { isoToday, replaceSecretSchema, type ReplaceSecretValues } from "../lib/ownAppSchema";
import { CopyableValue, CopyButton } from "./CopyableValue";
import { KeysRefusedAlert, RecallKeysNote, ScopeCodes } from "./OwnAppParts";
import { SecretInput } from "./SecretInput";

const MASK = "••••••••••••";

/** A saved own app, read-only: the secret is never shown, only replaced, and the provider can be asked again. */
export function SavedOwnApp({
  integration,
  sharesWithRecall,
  onReplaceSecret,
  onAskReturn,
  onChangeApp,
}: {
  integration: WorkspaceIntegration;
  sharesWithRecall: boolean;
  onReplaceSecret: (values: ReplaceSecretValues) => Promise<void>;
  onAskReturn: () => void;
  onChangeApp: () => void;
}) {
  const copy = INTEGRATION_PROVIDERS[integration.provider];
  const [replacing, setReplacing] = useState(false);
  const [setupOpen, setSetupOpen] = useState(false);
  const setupId = useId();
  const daysLeft = secretDaysLeft(integration);
  const masked = MASK + (integration.secretHint ?? "");
  const savedLine = secretMeta(integration);

  const handleReplace = async (values: ReplaceSecretValues) => {
    await onReplaceSecret(values);
    setReplacing(false);
  };

  return (
    <div>
      {integration.secretExpiresOn && daysLeft !== null && daysLeft <= EXPIRY_WARNING_DAYS && (
        <SecretExpiryNotice expiresOn={integration.secretExpiresOn} daysLeft={daysLeft} />
      )}

      <dl className="rounded-u-chip border border-u-border bg-u-surface">
        <SavedRow label={copy.idLabel}>
          <dd className="min-w-0 flex-1 font-mono text-note font-medium text-u-text [overflow-wrap:anywhere]">
            {integration.clientId}
          </dd>
          {integration.clientId && (
            <CopyButton value={integration.clientId} what={copy.idLabel} className={ROW_BUTTON}>
              Copy
            </CopyButton>
          )}
        </SavedRow>

        {integration.provider === "MICROSOFT" && (
          <SavedRow label="Directory (tenant) ID">
            <dd className="min-w-0 flex-1 font-mono text-note font-medium text-u-text [overflow-wrap:anywhere]">
              {integration.tenantId}
            </dd>
          </SavedRow>
        )}

        <SavedRow label={copy.secretLabel}>
          {replacing ? (
            <>
              <dd className="min-w-0 flex-1 font-mono text-meta text-u-text3">
                Replacing {masked}. It keeps working until you save.
              </dd>
              <dd className="basis-full">
                <ReplaceSecretForm
                  integration={integration}
                  copy={copy}
                  onSave={handleReplace}
                  onCancel={() => setReplacing(false)}
                />
              </dd>
            </>
          ) : (
            <>
              <dd className="min-w-0 flex-1">
                <span aria-hidden="true" className="font-mono text-note font-medium tracking-tag text-u-text">
                  {masked}
                </span>
                <span className="sr-only">
                  {integration.secretHint ? `Saved secret ending ${integration.secretHint}` : "Saved secret"}
                </span>
                {savedLine && <span className="mt-0.5 block font-mono text-meta text-u-text3">{savedLine}</span>}
              </dd>
              <button type="button" onClick={() => setReplacing(true)} className={ROW_BUTTON}>
                Replace
              </button>
            </>
          )}
        </SavedRow>

        {integration.secretExpiresOn && !replacing && (
          <SavedRow label="Secret expires">
            <dd className="min-w-0 flex-1 font-mono text-note font-medium text-u-text">
              {formatDate(integration.secretExpiresOn)}
              {daysLeft !== null && daysLeft >= 0 && daysLeft <= EXPIRY_WARNING_DAYS && ` · ${dayCount(daysLeft)} left`}
            </dd>
          </SavedRow>
        )}
      </dl>

      <KeysCheck integration={integration} copy={copy} />

      <div className="mt-3">
        <button
          type="button"
          aria-expanded={setupOpen}
          aria-controls={setupId}
          onClick={() => setSetupOpen((wasOpen) => !wasOpen)}
          className="flex cursor-pointer items-center gap-1.5 py-1 text-note font-medium text-u-text3 transition hover:text-u-text"
        >
          <Icon
            d={ICONS.chevronRight}
            size={12}
            className={cn("transition-transform duration-150", setupOpen && "rotate-90")}
          />
          Setup details: redirect URI and {copy.scopesNoun}
        </button>
        <FoldBody id={setupId} open={setupOpen}>
          <div className="flex flex-col gap-2.5 pt-2">
            <CopyableValue label="Redirect URI" value={integration.redirectUri} showLabel={false} />
            <ScopeCodes scopes={integration.scopes} label={copy.scopesNoun} />
          </div>
        </FoldBody>
      </div>

      {sharesWithRecall && <RecallKeysNote />}

      <div className="mt-4 flex flex-wrap items-center justify-between gap-2 border-t border-u-border pt-3.5">
        <button
          type="button"
          onClick={onAskReturn}
          className="cursor-pointer rounded-u-control px-2.5 py-2 text-body font-medium text-u-offlimits transition hover:bg-u-offlimits-tint"
        >
          Return to the shared app
        </button>
        <div className="flex flex-wrap items-center gap-2.5">
          <span className="font-mono text-meta text-u-text3">
            {connectionsLine(integration.provider, integration.connectionsThroughApp)}
          </span>
          <Button variant="secondary" size="sm" onClick={onChangeApp}>
            Use a different app
          </Button>
        </div>
      </div>
    </div>
  );
}

const ROW_BUTTON =
  "cursor-pointer rounded-u-control border border-u-border-strong px-2.5 py-1 text-note font-medium text-u-text2 transition hover:text-u-text";

function SavedRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-wrap items-center gap-x-3.5 gap-y-1.5 border-t border-u-border px-3.5 py-3 first:border-t-0">
      <dt className="w-43 flex-none type-micro-label font-mono text-u-text3">{label}</dt>
      {children}
    </div>
  );
}

/** "Ends Jx4q · saved 12 Sep 2026 by Yara Haddad", leaving out whatever was never recorded. */
function secretMeta(integration: WorkspaceIntegration): string | null {
  const savedOn = formatInstantDate(integration.secretSavedAt);
  const saved =
    savedOn || integration.secretSavedBy
      ? ["saved", savedOn, integration.secretSavedBy ? `by ${integration.secretSavedBy}` : null]
          .filter((part): part is string => !!part)
          .join(" ")
      : null;
  const line = [integration.secretHint ? `Ends ${integration.secretHint}` : null, saved]
    .filter((part): part is string => !!part)
    .join(" · ");
  return line ? line.charAt(0).toUpperCase() + line.slice(1) : null;
}

function SecretExpiryNotice({ expiresOn, daysLeft }: { expiresOn: string; daysLeft: number }) {
  const expired = daysLeft < 0;
  return (
    <p
      role="status"
      className={cn(
        "mb-3.5 rounded-u-chip px-3.5 py-2.5 text-note text-u-text",
        expired ? "bg-u-offlimits-tint" : "bg-u-signal-tint",
      )}
    >
      <b className={cn("font-semibold", expired ? "text-u-offlimits" : "text-u-signal")}>
        {expired
          ? `The client secret expired on ${formatDate(expiresOn)}.`
          : daysLeft === 0
            ? "The client secret expires today."
            : `The client secret expires on ${formatDate(expiresOn)}, in ${dayCount(daysLeft)}.`}
      </b>{" "}
      {expired
        ? "Mail, calendars and links through this app stop until a new secret is saved. Replace it below."
        : "Add a new secret in Certificates & secrets, then Replace it below. The old one works until you do."}
    </p>
  );
}

/** What the provider last said of these keys, and Verify again to ask it now. */
function KeysCheck({ integration, copy }: { integration: WorkspaceIntegration; copy: IntegrationProviderCopy }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const verify = useMutation({
    mutationFn: (_wasRefused: boolean) => integrationsApi.verifyIntegration(integration.provider),
    onSuccess: (page, wasRefused) => {
      queryClient.setQueryData(integrationsApi.INTEGRATIONS_KEY, page);
      const refused = page.providers.find((provider) => provider.provider === integration.provider)?.keysRefusedWith;
      if (!refused) toast(`${copy.short} accepted these keys`);
      else toast(wasRefused ? `${copy.short} still refuses these keys` : `${copy.short} refused these keys`);
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  const refusedWith = integration.keysRefusedWith;
  const checkedAt = integration.keysCheckedAt;

  return (
    <div role="status" className="mt-3 flex flex-wrap items-center gap-2">
      {(refusedWith || checkedAt) && (
        <span
          aria-hidden="true"
          className={cn(
            "grid size-5 flex-none place-items-center rounded-full text-meta font-bold",
            refusedWith ? "bg-u-offlimits-tint text-u-offlimits" : "bg-u-direct-tint text-u-direct",
          )}
        >
          {refusedWith ? "✕" : "✓"}
        </span>
      )}
      <span className="min-w-0 flex-1 text-note text-u-text2">
        {verify.isPending
          ? `Checking these keys with ${copy.short}…`
          : refusedWith
            ? `${copy.short} refused these keys (${refusedWith}: the secret is wrong, or was deleted in ${copy.secretHome}). Replace it with a current one; until then mail and calendars through this app wait.`
            : checkedAt
              ? `${copy.short} accepted these keys · checked ${formatTimeAgo(checkedAt)}`
              : `These keys haven't been checked with ${copy.short} yet.`}
      </span>
      <button
        type="button"
        disabled={verify.isPending}
        onClick={() => verify.mutate(!!refusedWith)}
        className="flex cursor-pointer items-center gap-1.5 rounded-u-control px-2.5 py-1 text-note font-medium text-u-accent transition hover:bg-u-accent-tint disabled:cursor-not-allowed disabled:opacity-60"
      >
        {verify.isPending && <Spinner />}
        {verify.isPending ? "Checking…" : checkedAt || refusedWith ? "Verify again" : "Verify now"}
      </button>
    </div>
  );
}

function ReplaceSecretForm({
  integration,
  copy,
  onSave,
  onCancel,
}: {
  integration: WorkspaceIntegration;
  copy: IntegrationProviderCopy;
  onSave: (values: ReplaceSecretValues) => Promise<void>;
  onCancel: () => void;
}) {
  const [failure, setFailure] = useState<SaveFailure | null>(null);
  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ReplaceSecretValues>({
    resolver: zodResolver(replaceSecretSchema({ provider: integration.provider, today: isoToday() })),
    defaultValues: { clientSecret: "", secretExpiresOn: "" },
  });

  const submit = async (values: ReplaceSecretValues) => {
    setFailure(null);
    try {
      await onSave(values);
    } catch (error) {
      const failed = failureOf(error, values, (field, message) => setError(field, { message }));
      if (failed) setFailure(failed);
    }
  };

  return (
    <form onSubmit={handleSubmit(submit)} noValidate className="mt-1.5">
      <div className="grid grid-cols-1 gap-x-3.5 md:grid-cols-2">
        <SecretInput
          label={`New ${copy.secretLabel.toLowerCase()}`}
          autoFocus
          error={errors.clientSecret?.message}
          hint={copy.onceNote}
          {...register("clientSecret")}
        />
        {integration.provider === "MICROSOFT" && (
          <Field
            label="New secret expires"
            error={errors.secretExpiresOn?.message}
            hint="A new secret has its own expiry. Entra allows 24 months at most."
          >
            <Controller
              control={control}
              name="secretExpiresOn"
              render={({ field }) => (
                <DateInput value={field.value} onChange={field.onChange} ariaLabel="New secret expires" />
              )}
            />
          </Field>
        )}
      </div>
      {failure && "refusedWith" in failure && (
        <KeysRefusedAlert
          short={copy.short}
          providerError={failure.refusedWith}
          consequence="The saved secret is unchanged."
        />
      )}
      <FormError message={failure && "message" in failure ? failure.message : null} />
      <div className="mt-3 flex flex-wrap justify-end gap-2">
        <Button variant="secondary" size="sm" onClick={onCancel}>
          Cancel
        </Button>
        <Button type="submit" size="sm" loading={isSubmitting}>
          {isSubmitting ? `Checking with ${copy.short}…` : "Save and verify"}
        </Button>
      </div>
    </form>
  );
}
