import { useEffect, useRef, useState, type ReactNode } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../../features/auth/AuthProvider";
import { Avatar } from "../ui";
import { Icon, ICONS } from "./Icon";

/**
 * The 46px header: the workspace dropdown (settings, members, sign out) on the left, the user's
 * avatar on the right. Settings screens pass a breadcrumb instead of the dropdown.
 */
export function Topbar({ breadcrumb }: { breadcrumb?: ReactNode }) {
  const { user } = useAuth();

  return (
    <header className="relative z-[60] flex h-[46px] flex-none items-center gap-3 px-3.5">
      {breadcrumb ?? <WorkspaceMenu />}
      <div className="ml-auto flex items-center gap-2.5">
        {user && <Avatar id={user.id} name={user.fullName} />}
      </div>
    </header>
  );
}

/** The breadcrumb variant: `[L] Workspace / Settings / {section}`. */
export function SettingsBreadcrumb({ section }: { section: string }) {
  const { user } = useAuth();
  const workspace = user?.workspace;

  return (
    <div className="flex items-center gap-2">
      <Link to="/" title="Back to workspace" className="flex items-center rounded-[7px] p-1 hover:bg-panel2">
        <LogoTile mark={workspace?.logoMark ?? "L"} />
      </Link>
      <Link
        to="/"
        className="whitespace-nowrap rounded-md px-1.5 py-1 font-mono text-[13px] font-medium text-text3 hover:bg-panel2 hover:text-text"
      >
        Workspace
      </Link>
      <span className="text-xs text-text3 opacity-40">/</span>
      <span className="whitespace-nowrap text-sm font-semibold text-text">Settings</span>
      <span className="text-xs text-text3 opacity-40">/</span>
      <span className="whitespace-nowrap font-mono text-[13px] font-medium text-text2">{section}</span>
    </div>
  );
}

function WorkspaceMenu() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const close = (event: MouseEvent) => {
      if (!ref.current?.contains(event.target as Node)) setOpen(false);
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", close);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", close);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  const workspace = user?.workspace;
  if (!workspace) return null;
  const isAdmin = workspace.roles.includes("ADMIN");

  const itemClass =
    "flex w-full items-center gap-2.5 rounded-[7px] px-2.5 py-2 text-left text-[13px] text-text2 " +
    "transition hover:bg-panel2 hover:text-text";

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        className="flex items-center gap-2 rounded-lg py-[5px] pl-1.5 pr-2.5 hover:bg-panel2"
      >
        <LogoTile mark={workspace.logoMark ?? workspace.name[0]} />
        <span className="font-mono text-[13px] font-semibold tracking-[0.02em]">{workspace.name}</span>
        <Icon d={ICONS.chevronDown} size={13} className="text-text3" />
      </button>

      {open && (
        <div className="absolute left-0 top-10 z-[80] w-[268px] rounded-[10px] border border-line bg-panel p-1.5 shadow-panel">
          <div className="mb-1.5 flex items-center gap-2.5 border-b border-line-soft p-2.5">
            <LogoTile mark={workspace.logoMark ?? workspace.name[0]} size={30} />
            <div>
              <div className="font-mono text-[13px] font-semibold">{workspace.name}</div>
              <div className="font-mono text-[11px] text-text3">{workspace.emailDomain}</div>
            </div>
          </div>

          {isAdmin && (
            <>
              <button type="button" className={itemClass} onClick={() => { setOpen(false); navigate("/settings/general"); }}>
                <Icon d={ICONS.settings} size={15} className="flex-none" />
                Workspace settings
              </button>
              <button type="button" className={itemClass} onClick={() => { setOpen(false); navigate("/settings/members"); }}>
                <Icon d={ICONS.members} size={15} className="flex-none" />
                Members
              </button>
              <div className="mx-1 my-1.5 h-px bg-line-soft" />
            </>
          )}

          <button type="button" className={itemClass} onClick={() => void signOut()}>
            <Icon d={ICONS.signOut} size={15} className="flex-none" />
            Sign out
          </button>
        </div>
      )}
    </div>
  );
}

function LogoTile({ mark, size = 22 }: { mark: string; size?: number }) {
  return (
    <span
      style={{ width: size, height: size }}
      className="grid place-items-center rounded-md bg-amber-btn font-mono text-[11px] font-bold text-on-amber"
    >
      {mark}
    </span>
  );
}
