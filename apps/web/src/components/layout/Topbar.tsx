import { useEffect, useState, type ReactNode } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../../features/auth/AuthProvider";
import type { PendingInvitation, WorkspaceSummary } from "../../features/auth/api/types";
import * as authApi from "../../features/auth/api/authApi";
import { isPureClient } from "../../features/auth/roles";
import { takeWorkspaceMove } from "../../features/auth/workspaceMoveNotice";
import { CreditChip } from "../../features/billing/components/CreditChip";
import { useTheme } from "../../features/theme/useTheme";
import { WorkspaceMark } from "../../features/workspace/components/WorkspaceMark";
import { AppIcon, Avatar, useToast } from "../ui";
import { cn } from "../../lib/cn";
import { messageFor } from "../../lib/errorCodes";
import { Icon, ICONS } from "./Icon";
import { useDropdownMenu } from "./useDropdownMenu";

/**
 * The 46px header: the workspace menu on the workspace's own name at the left, the account menu on the avatar at
 * the right — the convention people bring from every other tool. A project passes its people as `actions`, and
 * settings and project screens a breadcrumb led by the workspace menu. Below `lg` it also carries the nav drawer's
 * button.
 */
export function Topbar({
  breadcrumb,
  actions,
  navOpen = false,
  onMenuClick,
}: {
  breadcrumb?: ReactNode;
  /** Drawn beside the avatar — a project header's people. */
  actions?: ReactNode;
  navOpen?: boolean;
  onMenuClick?: () => void;
}) {
  const { user } = useAuth();
  const toast = useToast();

  useEffect(() => {
    const moved = takeWorkspaceMove();
    if (moved) toast(moved);
  }, [toast]);

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

      <div className="flex flex-none items-center gap-2.5">
        <CreditChip />
        {actions}
        {user && <AccountMenu />}
      </div>
    </header>
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
        className="hidden whitespace-nowrap rounded-md px-1.5 py-1 font-mono text-[13px] font-medium text-u-text3 hover:bg-u-raised hover:text-u-text lg:inline"
      >
        Positions
      </Link>
      <span className="hidden text-xs text-u-text3 opacity-40 lg:inline">/</span>
      <span className="hidden min-w-0 shrink items-center gap-1.5 whitespace-nowrap font-mono text-[13px] font-medium text-u-text2 sm:flex">
        <span className="size-1.5 flex-none rounded-full bg-u-accent" />
        <span className="truncate">{clientName}</span>
      </span>
      <span className="hidden text-xs text-u-text3 opacity-40 sm:inline">/</span>
      <span className="min-w-0 flex-1 truncate text-sm font-semibold text-u-text lg:max-w-[280px] lg:flex-initial">
        {positionTitle}
      </span>
    </div>
  );
}

/** The breadcrumb variant: `[mark] Workspace / Settings / {section}`. */
export function SettingsBreadcrumb({ section }: { section: string }) {
  const { user } = useAuth();
  return (
    <div className="flex min-w-0 items-center gap-2">
      <WorkspaceMenu compact />
      <Link
        to="/"
        className="hidden max-w-[200px] truncate whitespace-nowrap rounded-md px-1.5 py-1 font-mono text-[13px] font-medium text-u-text3 hover:bg-u-raised hover:text-u-text md:inline xl:hidden"
      >
        {user?.workspace?.name ?? "Workspace"}
      </Link>
      {/* From xl the trigger beside it carries the name, so the crumb steps aside rather than say it twice. */}
      <span className="hidden text-xs text-u-text3 opacity-40 md:inline xl:hidden">/</span>
      <span className="hidden whitespace-nowrap text-sm font-semibold text-u-text sm:inline">Settings</span>
      <span className="hidden text-xs text-u-text3 opacity-40 sm:inline">/</span>
      <span className="truncate font-mono text-[13px] font-medium text-u-text2">{section}</span>
    </div>
  );
}

function WorkspaceMenu({ compact = false }: { compact?: boolean }) {
  const { user, switchWorkspace, acceptAndSwitch } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const menu = useDropdownMenu();
  const [busy, setBusy] = useState(false);

  const workspace = user?.workspace;
  if (!workspace) return null;
  const isAdmin = workspace.roles.includes("ADMIN");
  const isStaff = !isPureClient(workspace.roles);
  const others = user.workspaces.filter((candidate) => candidate.id !== workspace.id);
  const invitations = user.pendingInvitations;

  const go = (path: string) => {
    menu.close();
    navigate(path);
  };

  const moveTo = async (work: () => Promise<unknown>) => {
    setBusy(true);
    try {
      await work();
      menu.close();
      navigate("/");
    } catch (error) {
      toast.error(messageFor(error));
    } finally {
      setBusy(false);
    }
  };

  const handleSwitch = (target: WorkspaceSummary) => moveTo(() => switchWorkspace(target.id));

  const handleAccept = (invitation: PendingInvitation) =>
    moveTo(() => acceptAndSwitch(() => authApi.acceptInvitationById(invitation.id)));

  const invitationCount =
    invitations.length === 1 ? "1 invitation to another workspace" : `${invitations.length} invitations to other workspaces`;

  return (
    <div className={cn("relative", compact ? "flex-none" : "min-w-0")} ref={menu.rootRef}>
      <button
        ref={menu.triggerRef}
        type="button"
        onClick={menu.toggle}
        {...menu.triggerProps}
        aria-label={`Workspace: ${workspace.name}${invitations.length > 0 ? ` · ${invitationCount}` : ""}`}
        title={workspace.name}
        className={cn(
          "flex min-w-0 items-center gap-2 rounded-lg hover:bg-u-raised aria-expanded:bg-u-raised",
          "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-u-accent",
          compact ? "py-1 pl-1 pr-2" : "py-[5px] pl-1.5 pr-2",
        )}
      >
        <AppIcon className="h-[22px]" />
        <span aria-hidden="true" className="hidden h-5 w-px flex-none bg-u-border sm:block" />
        <span className="relative flex-none">
          <WorkspaceMark workspace={workspace} size={22} />
          {invitations.length > 0 && (
            <span
              aria-hidden="true"
              className="absolute -right-2 -top-2 grid min-w-4 place-items-center rounded-full bg-u-signal px-1 text-[9.5px] font-semibold leading-4 text-white ring-2 ring-u-bg"
            >
              {invitations.length}
            </span>
          )}
        </span>
        <span
          className={cn(
            "min-w-0 truncate text-body font-semibold text-u-text",
            // A position's breadcrumb has room for the name only on a wide screen; a list page always shows it.
            compact ? "hidden max-w-[160px] xl:block" : "block max-w-[120px] sm:max-w-[240px]",
          )}
        >
          {workspace.name}
        </span>
        <Icon d={ICONS.chevronDown} size={13} className="flex-none text-u-text3" />
      </button>

      {menu.open && (
        <div
          ref={menu.menuRef}
          {...menu.menuProps}
          aria-label="Workspace"
          className="absolute left-0 top-10 z-[80] w-[min(280px,calc(100vw-24px))] rounded-[10px] border border-u-border-strong bg-u-surface p-1.5 shadow-u-e3"
        >
          <div role="presentation" className="mb-1.5 flex items-center gap-2.5 border-b border-u-border p-2.5">
            <WorkspaceMark workspace={workspace} size={30} />
            <div className="min-w-0">
              <div className="truncate text-body font-semibold">{workspace.name}</div>
              <div className="text-meta text-u-text3">Current workspace</div>
            </div>
          </div>

          {invitations.length > 0 && (
            <>
              <MenuHeading>Invitations</MenuHeading>
              {invitations.map((invitation) => (
                <MenuItem key={invitation.id} disabled={busy} onClick={() => void handleAccept(invitation)}>
                  <Icon d={ICONS.userPlus} size={15} className="flex-none" />
                  <span className="min-w-0 flex-1 truncate">Invited to {invitation.workspaceName}</span>
                  <span className="flex-none text-meta font-medium text-u-accent">Accept</span>
                </MenuItem>
              ))}
              <MenuDivider />
            </>
          )}
          {others.length > 0 && (
            <>
              <MenuHeading>Switch to</MenuHeading>
              {others.map((other) => (
                <MenuItem key={other.id} disabled={busy} onClick={() => void handleSwitch(other)}>
                  <WorkspaceMark workspace={other} size={20} />
                  <span className="min-w-0 flex-1 truncate">{other.name}</span>
                </MenuItem>
              ))}
              <MenuDivider />
            </>
          )}

          {isAdmin && (
            <MenuItem onClick={() => go("/settings/general")}>
              <Icon d={ICONS.settings} size={15} className="flex-none" />
              Workspace settings
            </MenuItem>
          )}
          {isStaff && (
            <MenuItem onClick={() => go("/team")}>
              <Icon d={ICONS.members} size={15} className="flex-none" />
              Team
            </MenuItem>
          )}
          <MenuItem onClick={() => go("/settings/workspaces")}>
            <Icon d={ICONS.allProjects} size={15} className="flex-none" />
            Manage workspaces
          </MenuItem>
          {isStaff && (
            <MenuItem onClick={() => go("/settings/workspaces?create=1")}>
              <Icon d={ICONS.plus} size={15} className="flex-none" />
              Create workspace
            </MenuItem>
          )}
        </div>
      )}
    </div>
  );
}

/** The avatar's menu: the person, not the workspace — their profile, security, theme, and signing out. */
function AccountMenu() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();
  const menu = useDropdownMenu();
  const { theme, toggle: toggleTheme } = useTheme();
  if (!user) return null;

  const go = (path: string) => {
    menu.close();
    navigate(path);
  };

  return (
    <div className="relative flex-none" ref={menu.rootRef}>
      <button
        ref={menu.triggerRef}
        type="button"
        onClick={menu.toggle}
        {...menu.triggerProps}
        aria-label={`Account: ${user.fullName}`}
        className="flex rounded-full outline-offset-2 hover:ring-2 hover:ring-u-border-strong focus-visible:outline-2 focus-visible:outline-u-accent aria-expanded:ring-2 aria-expanded:ring-u-border-strong"
      >
        <Avatar id={user.id} name={user.fullName} src={user.avatarUrl} />
      </button>

      {menu.open && (
        <div
          ref={menu.menuRef}
          {...menu.menuProps}
          aria-label="Account"
          className="absolute right-0 top-10 z-[80] w-[min(260px,calc(100vw-24px))] rounded-[10px] border border-u-border-strong bg-u-surface p-1.5 shadow-u-e3"
        >
          <div role="presentation" className="mb-1.5 border-b border-u-border p-2.5">
            <div className="truncate text-body font-semibold">{user.fullName}</div>
            <div className="truncate text-meta text-u-text3">{user.email}</div>
          </div>
          <MenuItem onClick={() => go("/settings/profile")}>
            <Icon d={ICONS.profile} size={15} className="flex-none" />
            Your profile
          </MenuItem>
          <MenuItem onClick={() => go("/settings/security")}>
            <Icon d={ICONS.lock} size={15} className="flex-none" />
            Security
          </MenuItem>
          <MenuItem role="menuitemcheckbox" checked={theme === "dark"} onClick={toggleTheme}>
            <Icon d={ICONS.moon} size={15} className="flex-none" />
            <span className="flex-1">Dark mode</span>
            <span aria-hidden="true" className="text-meta text-u-text3">{theme === "dark" ? "On" : "Off"}</span>
          </MenuItem>
          <MenuDivider />
          <MenuItem onClick={() => void signOut()}>
            <Icon d={ICONS.signOut} size={15} className="flex-none" />
            Sign out
          </MenuItem>
        </div>
      )}
    </div>
  );
}

function MenuItem({
  onClick,
  disabled = false,
  role = "menuitem",
  checked,
  children,
}: {
  onClick: () => void;
  disabled?: boolean;
  role?: "menuitem" | "menuitemcheckbox";
  checked?: boolean;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      role={role}
      aria-checked={role === "menuitemcheckbox" ? checked : undefined}
      // aria-disabled, not disabled: a busy item keeps its focus, so Escape and the arrows still reach the menu.
      aria-disabled={disabled || undefined}
      onClick={() => {
        if (!disabled) onClick();
      }}
      className={
        "flex w-full items-center gap-2.5 rounded-[7px] px-2.5 py-2 text-left text-[13px] text-u-text2 outline-none " +
        "transition hover:bg-u-raised hover:text-u-text focus-visible:bg-u-raised focus-visible:text-u-text aria-disabled:opacity-60"
      }
    >
      {children}
    </button>
  );
}

function MenuHeading({ children }: { children: ReactNode }) {
  return (
    <div role="presentation" className="px-2.5 pb-1 pt-1.5 font-mono text-[10px] font-semibold uppercase tracking-[0.14em] text-u-text3">
      {children}
    </div>
  );
}

function MenuDivider() {
  return <div role="separator" className="mx-1 my-1.5 h-px bg-u-border" />;
}
