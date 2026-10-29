import { QRCodeSVG } from "qrcode.react";
import { useEffect, useRef, useState } from "react";
import { FormError, LinesSkeleton } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as authApi from "../../auth/api/authApi";
import type { MfaSetup } from "../../auth/api/types";
import { CopyableValue } from "./CopyableValue";
import { TwoFactorChangeModal } from "./TwoFactorChangeModal";

/**
 * Turning two-factor on: a fresh secret as a QR code and a setup key, then the app's first code and the current
 * password. Loaded on demand, so the QR library is fetched only by someone turning it on. The secret lives in this
 * component's state alone and goes with it.
 */
export default function EnableTwoFactorModal({
  onEnabled,
  onClose,
}: {
  onEnabled: (recoveryCodes: string[]) => void;
  onClose: () => void;
}) {
  const [setup, setSetup] = useState<MfaSetup | null>(null);
  const [setupError, setSetupError] = useState<unknown>(null);

  // Once, though StrictMode runs effects twice: a second setup replaces the first secret, and the QR code on screen
  // would then be the one the server no longer holds.
  const started = useRef(false);
  useEffect(() => {
    if (started.current) return;
    started.current = true;
    authApi.startMfaSetup().then(setSetup, setSetupError);
  }, []);

  return (
    <TwoFactorChangeModal
      title="Turn on two-factor authentication"
      intro="Scan the QR code with your authenticator app, then enter the 6-digit code it shows."
      confirmLabel="Turn on"
      authenticatorOnly
      onConfirm={async (values) => {
        const { recoveryCodes } = await authApi.enableMfa(values);
        onEnabled(recoveryCodes);
      }}
      onClose={onClose}
    >
      {setup ? (
        <div className="mb-4 flex flex-col items-center gap-3">
          {/* Its own light ground and quiet zone: a camera reads dark modules on light, whatever the theme. */}
          <QRCodeSVG
            value={setup.otpauthUri}
            size={184}
            marginSize={2}
            title="QR code for your authenticator app"
            className="rounded-u-control"
          />

          <div className="w-full">
            <CopyableValue label="Setup key" value={setup.secret} />
          </div>
        </div>
      ) : setupError ? (
        <FormError message={messageFor(setupError)} />
      ) : (
        <div className="mb-4">
          <LinesSkeleton />
        </div>
      )}
    </TwoFactorChangeModal>
  );
}
