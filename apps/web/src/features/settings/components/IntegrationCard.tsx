import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Button, Modal, SegmentedControl, useToast, type SegmentedOption } from "../../../components/ui";
import { GoogleMark, MicrosoftMark, ZoomMark } from "../../../components/ui/BrandMarks";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { daysUntil, formatDate, formatInstantDate } from "../../../lib/format";
import * as integrationsApi from "../api/integrationsApi";
import type { CredentialMode, IntegrationProvider, WorkspaceIntegration } from "../api/types";
import type { OwnAppValues } from "../lib/ownAppSchema";
import { CopyableValue } from "./CopyableValue";
import { OwnAppForm } from "./OwnAppForm";

const MODE_OPTIONS: readonly SegmentedOption<CredentialMode>[] = [
  { value: "SHARED", label: "Shared app" },
  { value: "OWN", label: "Your own app" },
];

const PROVIDER_COPY: Record<
  IntegrationProvider,
  { title: string; covers: string; sharedSetup: string; Mark: typeof GoogleMark }
> = {
  GOOGLE: {
    Mark: GoogleMark,
    title: "Google Workspace",
    covers: "Gmail and Google Calendar.",
    sharedSetup:
      "Your Google Workspace admin marks Uncava as Trusted once (Admin console → Security → API controls), so nobody is stopped by Google's unverified-app screen.",
  },
  MICROSOFT: {
    Mark: MicrosoftMark,
    title: "Microsoft 365",
    covers: "Outlook mail and calendar, Teams meetings.",
    sharedSetup: "Send this link to your IT department. Approving it once lets every colleague connect in one click.",
  },
  ZOOM: {
    Mark: ZoomMark,
    title: "Zoom",
    covers: "Zoom links in Book a call.",
    sharedSetup: "Each consultant connects their own Zoom account in one click.",
  },
};

/** Zoom holds no calendar, so nothing of its app reaches Recall. */
const SYNCS_CALENDARS: Record<IntegrationProvider, boolean> = { GOOGLE: true, MICROSOFT: true, ZOOM: false };

/**
 * One provider on Settings → Integrations: Uncava's shared app, or the workspace's own. Choosing Own only opens
 * the form — nothing changes until it is saved; going back to Shared from a saved app discards its keys, so
 * that is confirmed first.
 */
export function IntegrationCard({
  integration,
  canStoreKeys,
  calendarSyncsThroughRecall,
}: {
  integration: WorkspaceIntegration;
  canStoreKeys: boolean;
  calendarSyncsThroughRecall: boolean;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  // Only "opened Own, not saved yet" is local: the saved mode is the server's, so a refetch that finds the
  // provider returned to the shared app by someone else redraws the card.
  const [openedOwn, setOpenedOwn] = useState(false);
  const shownMode: CredentialMode = integration.mode === "OWN" || openedOwn ? "OWN" : "SHARED";
  const [confirmingReturn, setConfirmingReturn] = useState(false);
  const copy = PROVIDER_COPY[integration.provider];

  const showSaved = (saved: Awaited<ReturnType<typeof integrationsApi.integrations>>) =>
    queryClient.setQueryData(integrationsApi.INTEGRATIONS_KEY, saved);

  const returning = useMutation({
    mutationFn: () => integrationsApi.returnToSharedApp(integration.provider),
    onSuccess: (saved) => {
      showSaved(saved);
      setConfirmingReturn(false);
      setOpenedOwn(false);
      toast(`${copy.title} uses the shared app`);
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  const handleChooseMode = (next: CredentialMode) => {
    if (next === "SHARED" && integration.mode === "OWN") {
      setConfirmingReturn(true);
      return;
    }
    setOpenedOwn(next === "OWN");
  };

  const handleSaveOwnApp = async (values: OwnAppValues) => {
    const saved = await integrationsApi.updateIntegration(integration.provider, {
      mode: "OWN",
      clientId: values.clientId,
      clientSecret: values.clientSecret.trim() ? values.clientSecret : undefined,
      tenantId: values.tenantId || undefined,
      secretExpiresOn: values.secretExpiresOn || null,
    });
    showSaved(saved);
    setOpenedOwn(false);
    toast(`${copy.title} uses your own app`);
  };

  return (
    <section aria-label={copy.title} className="rounded-[10px] border border-u-border bg-u-raised p-5">
      <div className="mb-3.5 flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-[12rem] flex-1 items-start gap-3">
          <span className="flex size-9 flex-none items-center justify-center rounded-lg border border-u-border bg-u-surface">
            <copy.Mark size={18} />
          </span>
          <div className="min-w-0">
            <div className="text-sm font-semibold">{copy.title}</div>
            <div className="mt-0.5 font-mono text-[11.5px] text-u-text3">{copy.covers}</div>
          </div>
        </div>
        <SegmentedControl label={`${copy.title} app`} options={MODE_OPTIONS} value={shownMode} onChange={handleChooseMode} />
      </div>

      {integration.mode === "OWN" && integration.secretExpiresOn && (
        <SecretExpiryNotice expiresOn={integration.secretExpiresOn} />
      )}

      {shownMode === "OWN" ? (
        <OwnAppForm
          key={integration.updatedAt ?? "new"}
          integration={integration}
          canStoreKeys={canStoreKeys}
          sharesWithRecall={calendarSyncsThroughRecall && SYNCS_CALENDARS[integration.provider]}
          onSave={handleSaveOwnApp}
        />
      ) : (
        <SharedAppDetails integration={integration} setup={copy.sharedSetup} />
      )}

      {confirmingReturn && (
        <Modal
          open
          onClose={() => setConfirmingReturn(false)}
          title={`Return ${copy.title} to the shared app?`}
          footer={
            <>
              <Button variant="secondary" onClick={() => setConfirmingReturn(false)}>
                Cancel
              </Button>
              <Button loading={returning.isPending} onClick={() => returning.mutate()}>
                Use the shared app
              </Button>
            </>
          }
        >
          <p className="text-body text-u-text2">
            Your app's client ID and secret are deleted from Uncava. To use your own app again, you'll enter them
            again.
          </p>
        </Modal>
      )}
    </section>
  );
}

/** The same 30 days the admins' warning emails start at. */
const EXPIRY_WARNING_DAYS = 30;

function SecretExpiryNotice({ expiresOn }: { expiresOn: string }) {
  const daysLeft = daysUntil(expiresOn);
  if (daysLeft > EXPIRY_WARNING_DAYS) {
    return null;
  }
  const expired = daysLeft < 0;
  return (
    <p
      role="status"
      className={cn(
        "mb-3.5 rounded-[8px] px-3.5 py-2.5 text-[13px] text-u-text",
        expired ? "bg-u-offlimits-tint" : "bg-u-signal-tint",
      )}
    >
      <b className={expired ? "text-u-offlimits" : "text-u-signal"}>
        {expired
          ? `The client secret expired on ${formatDate(expiresOn)}.`
          : daysLeft === 0
            ? "The client secret expires today."
            : `The client secret expires on ${formatDate(expiresOn)}, in ${daysLeft === 1 ? "1 day" : `${daysLeft} days`}.`}
      </b>{" "}
      {expired
        ? "Mail, calendars and links through this app stop until a new secret is saved below."
        : "Create a new one in the provider's console and save it below with its expiry date."}
    </p>
  );
}

function SharedAppDetails({ integration, setup }: { integration: WorkspaceIntegration; setup: string }) {
  if (!integration.sharedOffered) {
    return (
      <p className="font-mono text-[11.5px] text-u-text3">
        Uncava's shared app isn't available on this deployment yet. Use your own app to connect now.
      </p>
    );
  }
  return (
    <div className="space-y-2">
      {integration.adminConsentUrl && <CopyableValue label="Admin consent link" value={integration.adminConsentUrl} />}
      {integration.adminConsentedAt && (
        <p className="text-note text-u-text2">
          <span className="text-u-accent">✓</span> Approved for your organisation on{" "}
          {formatInstantDate(integration.adminConsentedAt)}
        </p>
      )}
      <p className="font-mono text-[11.5px] text-u-text3">
        {setup}{" "}
        {integration.sharedAppGuideUrl && (
          <a
            href={integration.sharedAppGuideUrl}
            target="_blank"
            rel="noreferrer"
            className="text-u-accent hover:underline"
          >
            Setup guide ↗
          </a>
        )}
      </p>
    </div>
  );
}
