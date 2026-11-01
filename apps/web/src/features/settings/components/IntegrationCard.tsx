import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useId, useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, SegmentedControl, useToast, type SegmentedOption } from "../../../components/ui";
import { GoogleMark, MicrosoftMark, ZoomMark } from "../../../components/ui/BrandMarks";
import { ConfirmDialog } from "../../../components/ui/ConfirmDialog";
import { FoldBody } from "../../../components/ui/FoldBody";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { formatInstantDate } from "../../../lib/format";
import * as integrationsApi from "../api/integrationsApi";
import type {
  CredentialMode,
  IntegrationProvider,
  UpdateIntegrationRequest,
  WorkspaceIntegration,
  WorkspaceIntegrations,
} from "../api/types";
import { connectionsThroughYourApp, reconnectsAsked } from "../lib/integrationConnections";
import { INTEGRATION_PROVIDERS, type IntegrationProviderCopy } from "../lib/integrationProviders";
import { integrationStatus, type IntegrationTone } from "../lib/integrationStatus";
import type { OwnAppValues, ReplaceSecretValues } from "../lib/ownAppSchema";
import { CopyableValue } from "./CopyableValue";
import { OwnAppSetupForm } from "./OwnAppSetupForm";
import { SavedOwnApp } from "./SavedOwnApp";

const MODE_OPTIONS: readonly SegmentedOption<CredentialMode>[] = [
  { value: "SHARED", label: "Shared app" },
  { value: "OWN", label: "Your own app" },
];

const MARKS: Record<IntegrationProvider, typeof GoogleMark> = {
  GOOGLE: GoogleMark,
  MICROSOFT: MicrosoftMark,
  ZOOM: ZoomMark,
};

const PILL_TONES: Record<IntegrationTone, string> = {
  neutral: "border border-u-border text-u-text2",
  muted: "border border-dashed border-u-border text-u-text3",
  ok: "bg-u-direct-tint text-u-direct",
  warning: "bg-u-signal-tint text-u-signal",
  failing: "bg-u-offlimits-tint text-u-offlimits",
};

const CAPTION = "font-mono text-[11.5px] text-u-text3";

/**
 * One provider on Settings → Integrations, closed to its status until Manage: Uncava's shared app, or the workspace's
 * own. Choosing Own only opens the set-up form — nothing changes until Save and verify; going back to Shared from a
 * saved app discards its keys, so that is confirmed first.
 */
export function IntegrationCard({
  integration,
  canStoreKeys,
  calendarSyncsThroughRecall,
  defaultOpen = false,
}: {
  integration: WorkspaceIntegration;
  canStoreKeys: boolean;
  calendarSyncsThroughRecall: boolean;
  defaultOpen?: boolean;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const id = useId();
  const bodyId = `${id}-body`;
  const statusId = `${id}-status`;
  const summaryId = `${id}-summary`;
  const copy = INTEGRATION_PROVIDERS[integration.provider];
  const Mark = MARKS[integration.provider];
  const [open, setOpen] = useState(defaultOpen);
  // Only "setting up an app, not saved yet" is local: the saved mode is the server's, so a refetch that finds the
  // provider returned to the shared app by someone else redraws the card.
  const [settingUp, setSettingUp] = useState(false);
  const [confirmingReturn, setConfirmingReturn] = useState(false);
  const isSaved = integration.mode === "OWN";
  const shownMode: CredentialMode = isSaved || settingUp ? "OWN" : "SHARED";
  const status = integrationStatus(integration, { openedOwn: settingUp });
  const sharesWithRecall = calendarSyncsThroughRecall && copy.syncsCalendars;

  const showSaved = (saved: WorkspaceIntegrations) => queryClient.setQueryData(integrationsApi.INTEGRATIONS_KEY, saved);

  const returning = useMutation({
    mutationFn: (_connections: number) => integrationsApi.returnToSharedApp(integration.provider),
    onSuccess: (saved, connections) => {
      showSaved(saved);
      setConfirmingReturn(false);
      setSettingUp(false);
      toast(
        connections > 0
          ? `${copy.title} uses the shared app. ${reconnectsAsked(integration.provider, connections)}`
          : `${copy.title} uses the shared app`,
      );
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  const handleChooseMode = (next: CredentialMode) => {
    if (next === shownMode) return;
    if (next === "OWN") {
      setSettingUp(true);
    } else if (isSaved) {
      setConfirmingReturn(true);
    } else {
      setSettingUp(false);
    }
  };

  const saveKeys = async (keys: UpdateIntegrationRequest, savedMessage: string) => {
    showSaved(await integrationsApi.updateIntegration(integration.provider, keys));
    toast(savedMessage);
  };

  const handleSaveNewApp = async (values: OwnAppValues) => {
    await saveKeys(
      {
        mode: "OWN",
        clientId: values.clientId,
        clientSecret: values.clientSecret,
        tenantId: values.tenantId || undefined,
        secretExpiresOn: integration.provider === "MICROSOFT" ? values.secretExpiresOn : null,
      },
      `${copy.title} uses your own app. ${copy.short} accepted the keys`,
    );
    setSettingUp(false);
  };

  const handleReplaceSecret = (values: ReplaceSecretValues) =>
    saveKeys(
      {
        mode: "OWN",
        clientId: integration.clientId ?? undefined,
        clientSecret: values.clientSecret,
        tenantId: integration.tenantId ?? undefined,
        secretExpiresOn: integration.provider === "MICROSOFT" ? values.secretExpiresOn : null,
      },
      `Secret replaced. ${copy.short} accepted it`,
    );

  return (
    <section
      aria-label={copy.title}
      aria-describedby={`${statusId} ${summaryId}`}
      className="rounded-[10px] border border-u-border bg-u-raised"
    >
      <div className="flex flex-wrap items-center gap-3 px-5 py-4">
        <span className="flex size-9 flex-none items-center justify-center rounded-u-chip border border-u-border bg-u-surface">
          <Mark size={18} />
        </span>
        <div className="min-w-48 flex-1">
          <div className="text-sm font-semibold">{copy.title}</div>
          <div id={summaryId} className={cn("mt-0.5 [overflow-wrap:anywhere]", CAPTION)}>
            {status.summary}
          </div>
        </div>
        <span
          id={statusId}
          className={cn(
            "flex-none whitespace-nowrap rounded-full px-2.5 py-0.5 font-mono text-meta font-semibold tracking-label",
            PILL_TONES[status.tone],
          )}
        >
          {status.pill}
        </span>
        <Button
          variant="secondary"
          size="sm"
          aria-expanded={open}
          aria-controls={bodyId}
          aria-label={`${open ? "Close" : "Manage"} ${copy.title}`}
          onClick={() => setOpen((wasOpen) => !wasOpen)}
        >
          {open ? "Close" : "Manage"}
          <Icon d={ICONS.chevronDown} size={13} className={cn("transition-transform duration-150", open && "rotate-180")} />
        </Button>
      </div>

      <FoldBody id={bodyId} open={open}>
        <div className="border-t border-u-border px-5 pb-5 pt-4.5">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-2.5">
            <div className={CAPTION}>{copy.covers}</div>
            <SegmentedControl
              label={`${copy.title} app`}
              options={MODE_OPTIONS}
              value={shownMode}
              onChange={handleChooseMode}
            />
          </div>

          {shownMode === "SHARED" ? (
            <SharedAppDetails integration={integration} copy={copy} />
          ) : settingUp ? (
            <OwnAppSetupForm
              integration={integration}
              canStoreKeys={canStoreKeys}
              sharesWithRecall={sharesWithRecall}
              onSave={handleSaveNewApp}
              onCancel={() => setSettingUp(false)}
            />
          ) : (
            <SavedOwnApp
              integration={integration}
              sharesWithRecall={sharesWithRecall}
              onReplaceSecret={handleReplaceSecret}
              onAskReturn={() => setConfirmingReturn(true)}
              onChangeApp={() => setSettingUp(true)}
            />
          )}
        </div>
      </FoldBody>

      <ConfirmDialog
        open={confirmingReturn}
        title={`Return ${copy.title} to the shared app?`}
        confirmLabel="Use the shared app"
        pending={returning.isPending}
        onConfirm={() => returning.mutate(integration.connectionsThroughApp)}
        onClose={() => setConfirmingReturn(false)}
      >
        <p>
          Your app's client ID and secret are deleted from Uncava
          {sharesWithRecall && ", and from the copies Recall.ai holds for your team's calendars"}. To use your own app
          again, you'll enter them again.
        </p>
        {integration.connectionsThroughApp > 0 && (
          <p>
            <b className="font-semibold text-u-text">
              {connectionsThroughYourApp(integration.provider, integration.connectionsThroughApp)}
            </b>{" "}
            Each connects again, once, through Uncava's app; until then their sequences wait.
          </p>
        )}
      </ConfirmDialog>
    </section>
  );
}

function SharedAppDetails({ integration, copy }: { integration: WorkspaceIntegration; copy: IntegrationProviderCopy }) {
  if (!integration.sharedOffered) {
    return (
      <p className={CAPTION}>
        Uncava's shared {copy.short} app isn't available yet. Use your own app to connect now.
      </p>
    );
  }
  return (
    <div className="space-y-2">
      {integration.adminConsentUrl && <CopyableValue label="Admin consent link" value={integration.adminConsentUrl} />}
      {integration.adminConsentedAt && (
        <p className="text-note text-u-text2">
          <span className="text-u-direct">✓</span> Approved for your organisation on{" "}
          {formatInstantDate(integration.adminConsentedAt)}
        </p>
      )}
      <p className={CAPTION}>
        {copy.sharedSetup}{" "}
        {integration.sharedAppGuideUrl && (
          <a
            href={integration.sharedAppGuideUrl}
            target="_blank"
            rel="noreferrer"
            className="text-u-accent hover:underline"
          >
            {copy.sharedGuide} ↗
          </a>
        )}
      </p>
    </div>
  );
}
