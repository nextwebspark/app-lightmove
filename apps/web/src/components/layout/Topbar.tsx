import { useEffect, useRef, useState, type ReactNode } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../../features/auth/AuthProvider";
import type { PendingInvitation, WorkspaceSummary } from "../../features/auth/api/types";
import * as authApi from "../../features/auth/api/authApi";
import { WorkspaceMark } from "../../features/workspace/components/WorkspaceMark";
import { AppIcon, Avatar, useToast } from "../ui";
import { messageFor } from "../../lib/errorCodes";
import { Icon, ICONS } from "./Icon";

/**
 * The 46px header: the workspace dropdown (settings, members, sign out) on the left, the user's
 * avatar on the right. Settings screens pass a breadcrumb instead of the dropdown. Below `lg` it
 * also carries the nav drawer's button.
 */
export function Topbar({
  breadcrumb,
  navOpen = false,
  onMenuClick,
}: {
  breadcrumb?: ReactNode;
  navOpen?: boolean;
  onMenuClick?: () => void;
}) {
  const { user } = useAuth();

  return (
    <header className="relative z-[60] flex h-[46px] flex-none items-center gap-2 px-3.5 sm:gap-3">
      {onMenuClick && (
        <button
          type="button"
          onClick={onMenuClick}
          aria-label="Open navigation"
          aria-expanded={navOpen}
          aria-controls="app-nav"
          className="-ml-1 flex size-9 flex-none items-center justify-center rounded-[7px] text-u-text2 transition hover:bg-u-raised hover:text-u-text lg:hidden"
        >
          <Icon d={ICONS.menu} size={18} />
        </button>
      )}

      <div className="flex min-w-0 flex-1 items-center">{breadcrumb ?? <WorkspaceMenu />}</div>

      <div className="flex min-w-0 flex-none items-center gap-2.5">
        {user?.workspace && <CurrentWorkspaceLabel workspace={user.workspace} />}
        {user && <Avatar id={user.id} name={user.fullName} src={user.avatarUrl} />}
      </div>
    </header>
  );
}

/** Which workspace the session is in, beside the avatar on every screen; a phone keeps only the mark. */
function CurrentWorkspaceLabel({ workspace }: { workspace: WorkspaceSummary }) {
  return (
    <span
      title={`Current workspace: ${workspace.name}`}
      className="flex min-w-0 items-center gap-2 rounded-[7px] border border-u-border px-1.5 py-1 sm:pr-2.5"
    >
      <WorkspaceMark workspace={workspace} size={18} />
      <span className="hidden max-w-[220px] truncate font-mono text-[12px] font-medium text-u-text2 sm:inline">
        {workspace.name}
      </span>
    </span>
  );
}

/** The project shell's breadcrumb: `[mark] Projects / {client} / {position title}` (mockup header). */
export function ProjectBreadcrumb({
  clientName,
  positionTitle,
}: {
  clientName: string;
  positionTitle: string;
}) {
  return (
    <div className="flex min-w-0 items-center gap-2">
      <WorkspaceMenu compact />
      <Link
        to="/"
        className="hidden whitespace-nowrap rounded-md px-1.5 py-1 font-mono text-[13px] font-medium text-u-text3 hover:bg-u-raised hover:text-u-text md:inline"
      >
        Positions
      </Link>
      <span className="hidden text-xs text-u-text3 opacity-40 md:inline">/</span>
      <span className="hidden items-center gap-1.5 whitespace-nowrap font-mono text-[13px] font-medium text-u-text2 sm:flex">
        <span className="size-1.5 rounded-full bg-u-accent" />
        {clientName}
      </span>
      <span className="hidden text-xs text-u-text3 opacity-40 sm:inline">/</span>
      <span className="min-w-0 flex-1 truncate text-sm font-semibold text-u-text lg:max-w-[280px] lg:flex-none">
        {positionTitle}
      </span>
    </div>
  );
}

/** The breadcrumb variant: `[mark] Workspace / Settings / {section}`. */
export function SettingsBreadcrumb({ section }: { section: string }) {
  return (
    <div className="flex min-w-0 items-center gap-2">
      <WorkspaceMenu compact />
      <Link
        to="/"
        className="hidden whitespace-nowrap rounded-md px-1.5 py-1 font-mono text-[13px] font-medium text-u-text3 hover:bg-u-raised hover:text-u-text md:inline"
      >
        Workspace
      </Link>
      <span className="hidden text-xs text-u-text3 opacity-40 md:inline">/</span>
      <span className="hidden whitespace-nowrap text-sm font-semibold text-u-text sm:inline">Settings</span>
      <span className="hidden text-xs text-u-text3 opacity-40 sm:inline">/</span>
      <span className="truncate font-mono text-[13px] font-medium text-u-text2">{section}</span>
    </div>
  );
}

/**
 * The dropdown under the mark. Its header is the workspace the session is in; when the user belongs
 * to others, or is invited to one, a Workspaces section lists them — switching is one click, and
 * accepting an invitation joins and switches. Manage workspaces (everyone's) is where a further one
 * is founded.
 */
function WorkspaceMenu({ compact = false }: { compact?: boolean }) {
  const { user, signOut, switchWorkspace } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
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
  const others = user.workspaces.filter((candidate) => candidate.id !== workspace.id);
  const invitations = user.pendingInvitations;

  const itemClass =
    "flex w-full items-center gap-2.5 rounded-[7px] px-2.5 py-2 text-left text-[13px] text-u-text2 " +
    "transition hover:bg-u-raised hover:text-u-text disabled:opacity-60";

  const moveTo = async (work: () => Promise<unknown>) => {
    setBusy(true);
    try {
      await work();
      setOpen(false);
      navigate("/");
    } catch (error) {
      toast(messageFor(error));
    } finally {
      setBusy(false);
    }
  };

  const handleSwitch = (target: WorkspaceSummary) => moveTo(() => switchWorkspace(target.id));

  // Joining is followed by switching: the natural next thing after accepting is to look at the place.
  const handleAccept = (invitation: PendingInvitation) =>
    moveTo(async () => {
      const joined = await authApi.acceptInvitationById(invitation.id);
      if (!joined.workspace) throw new Error("The invitation led to no workspace");
      await switchWorkspace(joined.workspace.id);
    });

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        title={compact ? workspace.name : undefined}
        className={
          compact
            ? "flex items-center gap-1 rounded-[7px] p-1 hover:bg-u-raised"
            : "flex items-center gap-2.5 rounded-lg py-[5px] pl-1.5 pr-2.5 hover:bg-u-raised"
        }
      >
        <AppIcon className="h-[30px]" />
        {!compact && (
          <span className="font-brand text-[14px] font-extralight uppercase tracking-[0.38em] text-u-text">Uncava</span>
        )}
        <Icon d={ICONS.chevronDown} size={13} className="text-u-text3" />
      </button>

      {open && (
        <div className="absolute left-0 top-10 z-[80] w-[min(268px,calc(100vw-24px))] rounded-[10px] border border-u-border-strong bg-u-surface p-1.5 shadow-u-e3">
          <div className="mb-1.5 flex items-center gap-2.5 border-b border-u-border p-2.5">
            <WorkspaceMark workspace={workspace} size={30} />
            <div className="min-w-0">
              <div className="truncate font-mono text-[13px] font-semibold">{workspace.name}</div>
              {others.length > 0 && (
                <div className="font-mono text-[10px] text-u-text3">Current workspace</div>
              )}
            </div>
          </div>

          {(others.length > 0 || invitations.length > 0) && (
            <>
              <div className="px-2.5 pb-1 pt-1.5 font-mono text-[10px] font-semibold uppercase tracking-[0.14em] text-u-text3">
                Workspaces
              </div>
              {others.map((other) => (
                <button
                  key={other.id}
                  type="button"
                  className={itemClass}
                  disabled={busy}
                  onClick={() => void handleSwitch(other)}
                >
                  <WorkspaceMark workspace={other} size={20} />
                  <span className="min-w-0 flex-1 truncate">{other.name}</span>
                </button>
              ))}
              {invitations.map((invitation) => (
                <button
                  key={invitation.id}
                  type="button"
                  className={itemClass}
                  disabled={busy}
                  onClick={() => void handleAccept(invitation)}
                >
                  <Icon d={ICONS.userPlus} size={15} className="flex-none" />
                  <span className="min-w-0 flex-1 truncate">Invited to {invitation.workspaceName}</span>
                  <span className="flex-none text-[11px] font-medium text-u-accent">Accept</span>
                </button>
              ))}
              <div className="mx-1 my-1.5 h-px bg-u-border" />
            </>
          )}

          <button type="button" className={itemClass} onClick={() => { setOpen(false); navigate("/settings/workspaces"); }}>
            <Icon d={ICONS.allProjects} size={15} className="flex-none" />
            Manage workspaces
          </button>
          <div className="mx-1 my-1.5 h-px bg-u-border" />

          {/* Outside the admin block on purpose: this is the one settings item that is everybody's,
              and for a pure client — whose rail carries no Settings link — it is the only way in. */}
          <button type="button" className={itemClass} onClick={() => { setOpen(false); navigate("/settings/profile"); }}>
            <Icon d={ICONS.profile} size={15} className="flex-none" />
            Your profile
          </button>
          <div className="mx-1 my-1.5 h-px bg-u-border" />

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
              <div className="mx-1 my-1.5 h-px bg-u-border" />
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

