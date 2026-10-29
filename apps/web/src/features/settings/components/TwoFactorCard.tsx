import { useQuery, useQueryClient } from "@tanstack/react-query";
import { lazy, Suspense, useState } from "react";
import { Link } from "react-router-dom";
import { Toggle, useToast } from "../../../components/ui";
import { formatInstantDate } from "../../../lib/format";
import * as authApi from "../../auth/api/authApi";
import { RecoveryCodesModal } from "./RecoveryCodesModal";
import { TwoFactorChangeModal } from "./TwoFactorChangeModal";

const EnableTwoFactorModal = lazy(() => import("./EnableTwoFactorModal"));

const MFA_QUERY_KEY = ["auth", "mfa"];

type OpenDialog = "enable" | "disable" | "regenerate" | null;

/**
 * The mockup's two-factor card. Shown even where it cannot be turned on, with the reason: this is the one screen where
 * someone comes looking for it, and an answer beats a missing card. Recovery codes are held in this component's state
 * only while their dialog is open, never in a query cache.
 */
export function TwoFactorCard({
  hasPassword,
  onSessionsChanged,
}: {
  hasPassword: boolean;
  /** Turning it on or off signs out every other device, so the sessions list beside it is stale. */
  onSessionsChanged: () => void;
}) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const status = useQuery({ queryKey: MFA_QUERY_KEY, queryFn: () => authApi.mfaStatus() });
  const [openDialog, setOpenDialog] = useState<OpenDialog>(null);
  const [revealedCodes, setRevealedCodes] = useState<string[] | null>(null);

  const refreshStatus = () => queryClient.invalidateQueries({ queryKey: MFA_QUERY_KEY });

  const handleEnabled = (codes: string[]) => {
    setOpenDialog(null);
    setRevealedCodes(codes);
    void refreshStatus();
    onSessionsChanged();
    toast("Two-factor authentication is on");
  };

  const handleDisable = async (values: { currentPassword: string; code: string }) => {
    await authApi.disableMfa(values);
    setOpenDialog(null);
    void refreshStatus();
    onSessionsChanged();
    toast("Two-factor authentication is off");
  };

  const handleRegenerate = async (values: { currentPassword: string; code: string }) => {
    const { recoveryCodes } = await authApi.regenerateRecoveryCodes(values);
    setOpenDialog(null);
    setRevealedCodes(recoveryCodes);
    void refreshStatus();
  };

  const enabled = status.data?.enabled ?? false;
  const canTurnOn = !!status.data?.offered && hasPassword;

  return (
    <div className="rounded-[10px] border border-u-border bg-u-raised p-5">
      <div className="flex items-center gap-3">
        <div className="flex-1">
          <div className="text-[13px] font-semibold">Two-factor authentication</div>
          <div className="mt-[3px] font-mono text-[11.5px] text-u-text3">
            Require a code from your authenticator app when signing in.
          </div>
        </div>

        {status.isSuccess && !enabled && !status.data.offered && (
          <span className="font-mono text-[11.5px] text-u-text3">Not available yet</span>
        )}
        <Toggle
          checked={enabled}
          disabled={!status.isSuccess || (!enabled && !canTurnOn)}
          label="Two-factor authentication"
          onChange={() => setOpenDialog(enabled ? "disable" : "enable")}
        />
      </div>

      {status.isError && (
        <p className="mt-3 font-mono text-meta text-u-text3">Couldn't load your two-factor settings.</p>
      )}

      {status.isSuccess && !enabled && status.data.offered && !hasPassword && (
        <p className="mt-3 font-mono text-meta text-u-text3">
          You sign in with a connected provider. To turn this on,{" "}
          <Link to="/forgot-password">send yourself a set-password link</Link> first.
        </p>
      )}

      {status.isSuccess && enabled && (
        <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 font-mono text-meta text-u-text3">
          <span>
            On since {formatInstantDate(status.data.enabledAt)} ·{" "}
            {status.data.recoveryCodesLeft === 1
              ? "1 recovery code left"
              : `${status.data.recoveryCodesLeft} recovery codes left`}
          </span>
          <button
            type="button"
            onClick={() => setOpenDialog("regenerate")}
            className="font-medium text-u-accent hover:underline"
          >
            Get new recovery codes
          </button>
        </div>
      )}

      {openDialog === "enable" && (
        <Suspense fallback={null}>
          <EnableTwoFactorModal onEnabled={handleEnabled} onClose={() => setOpenDialog(null)} />
        </Suspense>
      )}

      {openDialog === "disable" && (
        <TwoFactorChangeModal
          title="Turn off two-factor authentication?"
          intro="Signing in will ask for your password alone, and every other device will be signed out."
          confirmLabel="Turn off"
          tone="danger"
          onConfirm={handleDisable}
          onClose={() => setOpenDialog(null)}
        />
      )}

      {openDialog === "regenerate" && (
        <TwoFactorChangeModal
          title="Get new recovery codes"
          intro="Your current recovery codes will stop working, including any you have left."
          confirmLabel="Get new codes"
          onConfirm={handleRegenerate}
          onClose={() => setOpenDialog(null)}
        />
      )}

      {revealedCodes && <RecoveryCodesModal codes={revealedCodes} onDone={() => setRevealedCodes(null)} />}
    </div>
  );
}
