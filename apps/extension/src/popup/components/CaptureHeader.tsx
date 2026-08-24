import type { WorkspaceUser } from "../../api/types";
import { BrandTile, InitialsAvatar } from "./PopupChrome";

interface CaptureHeaderProps {
  user: WorkspaceUser | null;
  onSignOut?: () => void;
}

/** The popup's title bar: logo, product name, and who the extension is paired as. */
export function CaptureHeader({ user, onSignOut }: CaptureHeaderProps) {
  return (
    <header className="flex items-center gap-[9px] border-b border-line-soft px-3.5 py-[11px]">
      <BrandTile />
      <span className="flex-1 font-mono text-[12.5px] font-semibold tracking-[0.02em]">Capture</span>
      {user && onSignOut && (
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
