import type { WorkspaceUser } from "../../api/types";
import { BrandTile, InitialsAvatar } from "./PopupChrome";
import { Icon, ICONS } from "./Icon";

interface CaptureHeaderProps {
  user: WorkspaceUser | null;
  onOpenSettings: () => void;
  onSignOut: () => void;
}

/** The popup's title bar: logo, product name, settings, and who the extension is paired as. */
export function CaptureHeader({ user, onOpenSettings, onSignOut }: CaptureHeaderProps) {
  return (
    <header className="flex items-center gap-[9px] border-b border-line-soft px-3.5 py-[11px]">
      <BrandTile />
      <span className="flex-1 font-mono text-[12.5px] font-semibold tracking-[0.02em]">Capture</span>
      <button
        type="button"
        onClick={onOpenSettings}
        aria-label="Settings"
        className="grid h-[26px] w-[26px] place-items-center rounded-[7px] border border-line text-[13px] text-text2 hover:text-text"
      >
        <Icon d={ICONS.settings} />
      </button>
      {user && (
        <button
          type="button"
          onClick={onSignOut}
          title={`${user.fullName} · ${user.email} — sign out of the extension`}
          className="rounded-full focus:outline-none focus:ring-1 focus:ring-sky"
        >
          <InitialsAvatar name={user.fullName} />
        </button>
      )}
    </header>
  );
}
