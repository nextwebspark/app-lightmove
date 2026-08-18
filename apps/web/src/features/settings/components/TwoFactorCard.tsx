import { Toggle } from "../../../components/ui";

/**
 * The mockup's two-factor card, with the switch inert until TOTP exists.
 *
 * Shown rather than hidden on purpose: this is the one screen where someone comes looking for it, and
 * "not available yet" is an answer where a missing card is a hunt.
 */
export function TwoFactorCard() {
  return (
    <div className="rounded-[10px] border border-line-soft bg-panel2 p-5">
      <div className="flex items-center gap-3">
        <div className="flex-1">
          <div className="text-[13px] font-semibold">Two-factor authentication</div>
          <div className="mt-[3px] font-mono text-[11.5px] text-text3">
            Require a code from your authenticator app when signing in.
          </div>
        </div>

        <span className="font-mono text-[11.5px] text-text3">Not available yet</span>
        <Toggle checked={false} disabled label="Two-factor authentication" />
      </div>
    </div>
  );
}
